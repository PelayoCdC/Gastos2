package es.gastos.compartido.mensajeria;

import es.gastos.compartido.mensajeria.OutboxRepositorio.FilaOutbox;
import es.gastos.contratos.CatalogoEventos;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.ids.Uuid7;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de {@link PublicadorEventos} con el patrón transactional outbox: traduce cada evento de
 * dominio a su contrato y lo inserta en la tabla {@code outbox} <b>en la transacción en curso</b>.
 * Tras el commit despierta al relay para que lo envíe a Kafka sin esperar al siguiente sondeo.
 */
public class PublicadorEventosOutbox implements PublicadorEventos {

	private final TraductorEventos traductor;

	private final OutboxRepositorio outbox;

	private final JsonMapper json;

	private final Clock reloj;

	private final Runnable despertarRelay;

	private final String contexto;

	public PublicadorEventosOutbox(TraductorEventos traductor, OutboxRepositorio outbox, JsonMapper json, Clock reloj,
			Runnable despertarRelay, String contexto) {
		this.traductor = traductor;
		this.outbox = outbox;
		this.json = json;
		this.reloj = reloj;
		this.despertarRelay = despertarRelay;
		this.contexto = contexto;
	}

	@Override
	public void publicar(List<? extends EventoDominio> eventos) {
		if (eventos.isEmpty()) {
			return;
		}

		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			// Publicar fuera de una transacción rompería la garantía "no hay cambio sin evento"
			throw new IllegalStateException("Los eventos se publican dentro de una UnidadDeTrabajo");
		}

		for (EventoDominio evento : eventos) {
			Optional<EventoIntegracion> integracion = this.traductor.traducir(evento);
			integracion.ifPresent(datos -> this.escribir(evento, datos));
		}

		this.despertarTrasCommit();
	}

	private void escribir(EventoDominio evento, EventoIntegracion datos) {
		CatalogoEventos.Entrada entrada = CatalogoEventos.de(datos.getClass());
		long secuencia = this.outbox.insertar(new FilaOutbox(0, Uuid7.generar(), entrada.topic(), entrada.tipo(),
				entrada.version(), evento.usuarioId().valor(), evento.agregadoId(), evento.versionAgregado(),
				this.reloj.instant(), this.json.writeValueAsString(datos), null));
		SecuenciaPublicada.registrar(this.contexto, secuencia);
	}

	private void despertarTrasCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					PublicadorEventosOutbox.this.despertarRelay.run();
				}
			});
		}
	}
}
