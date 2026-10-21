package es.gastos.kernel.dominio;

/**
 * El recurso no existe <b>o pertenece a otro usuario</b>: ambos casos dan la misma respuesta
 * (404) para no revelar la existencia de datos ajenos.
 */
public class RecursoNoEncontrado extends ExcepcionDominio {

	public RecursoNoEncontrado(String recurso, Object id) {
		super("RECURSO_NO_ENCONTRADO", "No existe " + recurso + " " + id);
	}
}
