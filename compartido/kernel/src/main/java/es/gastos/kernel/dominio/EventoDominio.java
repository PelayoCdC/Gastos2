package es.gastos.kernel.dominio;

import java.util.UUID;

/**
 * Algo relevante que ha ocurrido en un agregado, expresado en el lenguaje de su contexto.
 *
 * <p>Los eventos de dominio no salen del servicio tal cual: la infraestructura los traduce a
 * eventos de integración (el lenguaje publicado de {@code compartido/contratos}) antes de
 * escribirlos en el outbox. Así el modelo interno puede cambiar sin romper a los consumidores.
 */
public interface EventoDominio {

	UUID agregadoId();

	UsuarioId usuarioId();

	/**
	 * Versión que alcanzó el agregado con este cambio. Los consumidores la usan para descartar
	 * eventos antiguos que lleguen tarde o repetidos.
	 */
	long versionAgregado();
}
