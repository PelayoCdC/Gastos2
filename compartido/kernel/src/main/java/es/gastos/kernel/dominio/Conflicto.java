package es.gastos.kernel.dominio;

/**
 * La operación choca con el estado actual (nombre ya usado, cuenta con movimientos, cambio
 * concurrente...). Se traduce a 409.
 */
public class Conflicto extends ExcepcionDominio {

	public Conflicto(String codigo, String mensaje) {
		super(codigo, mensaje);
	}

	/** Bloqueo optimista: otro cambio guardó el agregado desde que se leyó. */
	public static Conflicto modificacionConcurrente(String recurso, Object id) {
		return new Conflicto("MODIFICACION_CONCURRENTE",
				recurso + " " + id + " se ha modificado mientras tanto; vuelve a cargarlo y reintenta");
	}

	public static Conflicto nombreDuplicado(String recurso, String nombre) {
		return new Conflicto("NOMBRE_DUPLICADO", "Ya existe " + recurso + " con el nombre '" + nombre + "'");
	}
}
