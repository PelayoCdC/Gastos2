package es.gastos.compartido.mensajeria;

import es.gastos.compartido.mensajeria.AplicacionPruebaMensajeria.AlguienSeRegistro;
import es.gastos.compartido.mensajeria.AplicacionPruebaMensajeria.CambioInterno;
import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.contratos.Sobre;
import es.gastos.contratos.Topics;
import es.gastos.contratos.identidad.v1.UsuarioRegistrado;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.kernel.ids.Uuid7;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = AplicacionPruebaMensajeria.class, properties = "gastos.mensajeria.contexto=identidad")
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class OutboxIT {

	@Autowired
	private UnidadDeTrabajo unidadDeTrabajo;

	@Autowired
	private PublicadorEventos publicador;

	@Autowired
	private LectorSobres lector;

	@Autowired
	private Inbox inbox;

	@Autowired
	private JdbcClient jdbc;

	private KafkaConsumer<String, String> consumidor;

	@BeforeEach
	void suscribirse() {
		this.consumidor = new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ContenedoresKafka.KAFKA.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer());
		this.consumidor.subscribe(List.of(Topics.IDENTIDAD));
	}

	@AfterEach
	void cerrar() {
		this.consumidor.close();
	}

	@Test
	void unEventoPublicadoEnLaUnidadDeTrabajoLlegaAKafkaEnSuSobre() {
		UsuarioId usuario = new UsuarioId(Uuid7.generar());
		UUID agregado = Uuid7.generar();

		this.unidadDeTrabajo.ejecutar(() -> this.publicador.publicar(List.of(
				new AlguienSeRegistro(agregado, usuario, 1, "ana@ejemplo.es"),
				new CambioInterno(agregado, usuario, 2))));

		List<ConsumerRecord<String, String>> recibidos = this.esperarMensajesDe(usuario, 1);
		ConsumerRecord<String, String> mensaje = recibidos.getFirst();
		Sobre<EventoIntegracion> sobre = this.lector.leer(mensaje.value()).orElseThrow();

		assertThat(mensaje.key()).isEqualTo(usuario.toString());
		assertThat(sobre.tipo()).isEqualTo(UsuarioRegistrado.TIPO);
		assertThat(sobre.contexto()).isEqualTo("identidad");
		assertThat(sobre.secuencia()).isPositive();
		assertThat(sobre.agregadoId()).isEqualTo(agregado);
		assertThat(sobre.versionAgregado()).isEqualTo(1);
		assertThat(sobre.datos()).isEqualTo(new UsuarioRegistrado(usuario.valor(), "ana@ejemplo.es", "Prueba"));
		assertThat(this.jdbc.sql("SELECT COUNT(*) FROM outbox WHERE usuario_id = ? AND publicado_en IS NOT NULL")
				.param(usuario.valor()).query(Long.class).single()).isEqualTo(1L);
	}

	@Test
	void siLaTransaccionFallaNoSePublicaNada() {
		UsuarioId usuario = new UsuarioId(Uuid7.generar());

		assertThatThrownBy(() -> this.unidadDeTrabajo.ejecutar(() -> {
			this.publicador.publicar(List.of(new AlguienSeRegistro(Uuid7.generar(), usuario, 1, "x@ejemplo.es")));
			throw new IllegalStateException("fallo de negocio tras publicar");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(this.jdbc.sql("SELECT COUNT(*) FROM outbox WHERE usuario_id = ?")
				.param(usuario.valor()).query(Long.class).single()).isZero();
	}

	@Test
	void publicarFueraDeUnaUnidadDeTrabajoEsUnError() {
		UsuarioId usuario = new UsuarioId(Uuid7.generar());

		assertThatThrownBy(() -> this.publicador.publicar(List.of(new AlguienSeRegistro(Uuid7.generar(), usuario, 1, "y@ejemplo.es"))))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void elInboxEjecutaElEfectoUnaSolaVezPorConsumidor() {
		UUID evento = Uuid7.generar();
		List<String> efectos = new ArrayList<>();

		boolean primera = this.unidadDeTrabajo.ejecutar(() -> this.inbox.procesarUnaVez(evento, "siembra", () -> efectos.add("a")));
		boolean segunda = this.unidadDeTrabajo.ejecutar(() -> this.inbox.procesarUnaVez(evento, "siembra", () -> efectos.add("b")));
		boolean otroConsumidor = this.unidadDeTrabajo.ejecutar(() -> this.inbox.procesarUnaVez(evento, "otro", () -> efectos.add("c")));

		assertThat(primera).isTrue();
		assertThat(segunda).isFalse();
		assertThat(otroConsumidor).isTrue();
		assertThat(efectos).containsExactly("a", "c");
	}

	private List<ConsumerRecord<String, String>> esperarMensajesDe(UsuarioId usuario, int cuantos) {
		List<ConsumerRecord<String, String>> recibidos = new ArrayList<>();
		long limite = System.currentTimeMillis() + 30_000;
		while (recibidos.size() < cuantos && System.currentTimeMillis() < limite) {
			for (ConsumerRecord<String, String> registro : this.consumidor.poll(Duration.ofMillis(300))) {
				if (usuario.toString().equals(registro.key())) {
					recibidos.add(registro);
				}
			}
		}
		assertThat(recibidos).as("mensajes recibidos de Kafka").hasSize(cuantos);
		return recibidos;
	}
}
