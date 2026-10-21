package es.gastos.kernel.aplicacion;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.RaizAgregado;

import java.util.List;

/**
 * Puerto de salida para publicar eventos de dominio fuera del servicio.
 *
 * <p>Debe llamarse <b>dentro</b> de la {@link UnidadDeTrabajo} que guarda el agregado: la
 * implementación escribe en la tabla outbox en la misma transacción y un proceso aparte los
 * envía a Kafka (patrón transactional outbox, ver {@code docs/adr/0005}).
 */
public interface PublicadorEventos {

	void publicar(List<? extends EventoDominio> eventos);

	default void publicarDe(RaizAgregado<?> agregado) {
		this.publicar(agregado.extraerEventos());
	}
}
