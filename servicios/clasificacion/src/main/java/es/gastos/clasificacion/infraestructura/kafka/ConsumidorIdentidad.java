package es.gastos.clasificacion.infraestructura.kafka;

import es.gastos.clasificacion.aplicacion.siembra.SembrarCatalogoInicial;
import es.gastos.compartido.mensajeria.Inbox;
import es.gastos.compartido.mensajeria.LectorSobres;
import es.gastos.contratos.Topics;
import es.gastos.contratos.identidad.v1.UsuarioRegistrado;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: al registrarse un usuario, siembra su catálogo inicial. Usa el inbox para no
 * repetir el efecto si Kafka entrega el evento dos veces (la siembra además es idempotente por sí
 * misma: el inbox es la garantía general, la marca de siembra la específica).
 */
@Component
class ConsumidorIdentidad {

	static final String CONSUMIDOR = "clasificacion.siembra";

	private final LectorSobres lector;

	private final Inbox inbox;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final SembrarCatalogoInicial siembra;

	ConsumidorIdentidad(LectorSobres lector, Inbox inbox, UnidadDeTrabajo unidadDeTrabajo, SembrarCatalogoInicial siembra) {
		this.lector = lector;
		this.inbox = inbox;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.siembra = siembra;
	}

	@KafkaListener(topics = Topics.IDENTIDAD, groupId = "clasificacion")
	void recibir(String mensaje) {
		this.lector.leer(mensaje).ifPresent(sobre -> {
			if (sobre.datos() instanceof UsuarioRegistrado registrado) {
				this.unidadDeTrabajo.ejecutar(() -> this.inbox.procesarUnaVez(sobre.eventoId(), CONSUMIDOR,
						() -> this.siembra.asegurar(new UsuarioId(registrado.usuarioId()))));
			}
		});
	}
}
