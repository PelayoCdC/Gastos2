package es.gastos.contratos;

/**
 * Topics de Kafka: uno por contexto productor, con el {@code usuarioId} como clave para que los
 * eventos de cada usuario lleguen en orden (docs/adr/0004).
 */
public final class Topics {

	public static final String IDENTIDAD = "identidad.eventos";
	public static final String MOVIMIENTOS = "movimientos.eventos";
	public static final String CLASIFICACION = "clasificacion.eventos";
	public static final String IMPORTACION = "importacion.eventos";

	private Topics() {
	}

	/** Topic de mensajes fallidos de un servicio consumidor (uno por servicio, no por topic de origen). */
	public static String dlt(String servicio) {
		return servicio + ".dlt";
	}
}
