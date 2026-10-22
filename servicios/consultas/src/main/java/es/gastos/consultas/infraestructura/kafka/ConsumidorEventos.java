package es.gastos.consultas.infraestructura.kafka;

import es.gastos.compartido.mensajeria.LectorSobres;
import es.gastos.consultas.aplicacion.proyeccion.ProyectarEventos;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.contratos.Sobre;
import es.gastos.contratos.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de entrada: proyecta los eventos de movimientos y clasificacion <b>por lotes</b>.
 *
 * <p>Una importación genera cientos de eventos de golpe (una {@code TransaccionRegistrada} y una
 * {@code TransaccionClasificada} por fila); aplicarlos en una transacción por lote en vez de una por
 * mensaje es lo que permite que {@code Gastos-Esperar} se cumpla en unos cientos de milisegundos con un
 * extracto normal (medido en F8: 150 filas, 224 ms; 2000 filas, 2,6 s).
 *
 * <p>Si el lote falla, se repite mensaje a mensaje para aislar el culpable: los anteriores quedan
 * aplicados y el culpable se señala con {@link BatchListenerFailedException}, de modo que el
 * manejador de errores lo reintenta y, si sigue fallando, lo manda al DLT sin bloquear la partición.
 * Reaplicar los buenos no hace daño: las proyecciones son idempotentes por versión.
 *
 * <p>El grupo lleva la versión de la proyección ({@code consultas-v1}): cambiarla hace que un grupo
 * nuevo lea los topics desde el principio, que es como se reconstruye consultas (F9, docs/adr/0010).
 */
@Component
class ConsumidorEventos {

	private static final Logger LOG = LoggerFactory.getLogger(ConsumidorEventos.class);

	private final LectorSobres lector;

	private final ProyectarEventos proyectar;

	ConsumidorEventos(LectorSobres lector, ProyectarEventos proyectar) {
		this.lector = lector;
		this.proyectar = proyectar;
	}

	@KafkaListener(topics = {Topics.MOVIMIENTOS, Topics.CLASIFICACION},
			groupId = "consultas-v${gastos.consultas.proyeccion.version:1}", batch = "true")
	void recibir(List<String> mensajes) {
		List<Optional<Sobre<EventoIntegracion>>> sobres = new ArrayList<>(mensajes.size());
		for (int i = 0; i < mensajes.size(); i++) {
			try {
				sobres.add(this.lector.leer(mensajes.get(i)));
			} catch (RuntimeException ilegible) {
				throw new BatchListenerFailedException("Mensaje ilegible", ilegible, i);
			}
		}

		try {
			this.proyectar.aplicar(sobres.stream().flatMap(Optional::stream).toList());
		} catch (RuntimeException falloDelLote) {
			LOG.warn("Fallo al proyectar un lote de {} mensajes; se reintenta uno a uno", mensajes.size(), falloDelLote);
			this.unoAUno(sobres);
		}
	}

	private void unoAUno(List<Optional<Sobre<EventoIntegracion>>> sobres) {
		for (int i = 0; i < sobres.size(); i++) {
			try {
				this.proyectar.aplicar(sobres.get(i).stream().toList());
			} catch (RuntimeException fallo) {
				throw new BatchListenerFailedException("No se pudo proyectar el mensaje", fallo, i);
			}
		}
	}
}
