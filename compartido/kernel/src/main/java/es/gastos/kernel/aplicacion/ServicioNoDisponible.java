package es.gastos.kernel.aplicacion;

/**
 * Otro servicio del que depende la operación no responde (caído, arrancando o sin red). No es un
 * error de negocio ni del cliente: se traduce a 503 para que el cliente sepa que puede reintentar.
 */
public class ServicioNoDisponible extends RuntimeException {

	public ServicioNoDisponible(String servicio, Throwable causa) {
		super("El servicio " + servicio + " no esta disponible", causa);
	}
}
