package es.gastos.compartido.mensajeria;

import es.gastos.compartido.mensajeria.OutboxRepositorio.FilaOutbox;
import es.gastos.contratos.Sobre;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Relay del outbox: lee los eventos pendientes en orden, los envía a Kafka con el usuario como clave
 * y los marca como publicados. Si Kafka falla, la transacción se deshace y los eventos siguen
 * pendientes para el siguiente intento (entrega al menos una vez, docs/adr/0005).
 *
 * <p>Todo se ejecuta en un único hilo propio, así que nunca hay dos envíos a la vez y el orden del
 * outbox se conserva en cada partición. Se ejecuta periódicamente y, además, cuando
 * {@link #despertar()} lo pide tras un commit, para que la latencia sea de milisegundos.
 */
public class RelayOutbox implements SmartLifecycle {

	private static final Logger LOG = LoggerFactory.getLogger(RelayOutbox.class);

	private static final Duration ESPERA_ENVIO = Duration.ofSeconds(30);

	private static final Duration INTERVALO_LIMPIEZA = Duration.ofHours(1);

	private final OutboxRepositorio outbox;

	private final KafkaTemplate<String, String> kafka;

	private final TransactionTemplate transaccion;

	private final JsonMapper json;

	private final Clock reloj;

	private final PropiedadesMensajeria propiedades;

	private final AtomicBoolean despertado = new AtomicBoolean();

	private ScheduledExecutorService hilo;

	private volatile boolean enMarcha;

	public RelayOutbox(OutboxRepositorio outbox, KafkaTemplate<String, String> kafka, TransactionTemplate transaccion,
			JsonMapper json, Clock reloj, PropiedadesMensajeria propiedades) {
		this.outbox = outbox;
		this.kafka = kafka;
		this.transaccion = transaccion;
		this.json = json;
		this.reloj = reloj;
		this.propiedades = propiedades;
	}

	@Override
	public void start() {
		this.hilo = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("relay-outbox").daemon().factory());
		long intervalo = this.propiedades.intervaloRelay().toMillis();
		this.hilo.scheduleWithFixedDelay(this::ejecutar, intervalo, intervalo, TimeUnit.MILLISECONDS);
		this.hilo.scheduleWithFixedDelay(this::limpiar, 1, INTERVALO_LIMPIEZA.toMinutes(), TimeUnit.MINUTES);
		this.enMarcha = true;
	}

	@Override
	public void stop() {
		this.enMarcha = false;
		if (this.hilo != null) {
			this.hilo.shutdown();
		}
	}

	@Override
	public boolean isRunning() {
		return this.enMarcha;
	}

	/** Pide un envío inmediato. Varias peticiones seguidas se agrupan en una sola ejecución. */
	public void despertar() {
		if (this.enMarcha && this.despertado.compareAndSet(false, true)) {
			this.hilo.execute(this::ejecutar);
		}
	}

	private void ejecutar() {
		this.despertado.set(false);
		try {
			boolean quedanMas = true;
			while (quedanMas && this.enMarcha) {
				quedanMas = Boolean.TRUE.equals(this.transaccion.execute(estado -> this.enviarLote()));
			}
		} catch (RuntimeException e) {
			// Se reintenta en el siguiente ciclo; los eventos siguen pendientes en el outbox
			LOG.warn("No se pudo publicar el outbox, se reintentara: {}", e.getMessage());
		}
	}

	/** Envía un lote y devuelve si podía haber más pendientes. */
	private boolean enviarLote() {
		List<FilaOutbox> pendientes = this.outbox.bloquearPendientes(this.propiedades.loteRelay());
		if (pendientes.isEmpty()) {
			return false;
		}

		CompletableFuture<?>[] envios = pendientes.stream().map(this::enviar).toArray(CompletableFuture[]::new);
		try {
			CompletableFuture.allOf(envios).get(ESPERA_ENVIO.toMillis(), TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("Fallo enviando a Kafka", e);
		}

		this.outbox.marcarPublicados(pendientes.stream().map(FilaOutbox::id).toList(), this.reloj.instant());
		return pendientes.size() == this.propiedades.loteRelay();
	}

	private CompletableFuture<?> enviar(FilaOutbox fila) {
		JsonNode datos = this.json.readTree(fila.datos());
		Sobre<JsonNode> sobre = new Sobre<>(fila.eventoId(), fila.tipo(), this.propiedades.contexto(), fila.version(),
				fila.id(), fila.ocurridoEn(), fila.usuarioId(), fila.agregadoId(), fila.versionAgregado(), datos);

		ProducerRecord<String, String> registro = new ProducerRecord<>(fila.topic(), fila.usuarioId().toString(),
				this.json.writeValueAsString(sobre));
		registro.headers().add(new RecordHeader("tipo", fila.tipo().getBytes(StandardCharsets.UTF_8)));
		registro.headers().add(new RecordHeader("version", String.valueOf(fila.version()).getBytes(StandardCharsets.UTF_8)));
		if (fila.traceparent() != null) {
			registro.headers().add(new RecordHeader("traceparent", fila.traceparent().getBytes(StandardCharsets.UTF_8)));
		}

		return this.kafka.send(registro);
	}

	private void limpiar() {
		try {
			// En la misma transacción READ COMMITTED que el envío: el hueco que bloquearía es justo donde se
			// insertan los eventos nuevos
			Integer borrados = this.transaccion.execute(estado ->
					this.outbox.borrarPublicadosAntesDe(this.reloj.instant().minus(this.propiedades.retencionPublicados())));
			if (borrados != null && borrados > 0) {
				LOG.info("Limpieza del outbox: {} eventos publicados borrados", borrados);
			}
		} catch (RuntimeException e) {
			LOG.warn("No se pudo limpiar el outbox: {}", e.getMessage());
		}
	}
}
