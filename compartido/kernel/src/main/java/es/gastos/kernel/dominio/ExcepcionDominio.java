package es.gastos.kernel.dominio;

/**
 * Violación de una regla de negocio. Lleva un {@code codigo} estable (en MAYÚSCULAS) que el
 * cliente puede usar para decidir qué mostrar; el mensaje es solo orientativo.
 *
 * <p>La capa web la traduce a un ProblemDetail (RFC 9457) con estado 400; sus subclases
 * {@link RecursoNoEncontrado} y {@link Conflicto} se traducen a 404 y 409.
 */
public class ExcepcionDominio extends RuntimeException {

	private final String codigo;

	public ExcepcionDominio(String codigo, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
	}

	public String codigo() {
		return this.codigo;
	}
}
