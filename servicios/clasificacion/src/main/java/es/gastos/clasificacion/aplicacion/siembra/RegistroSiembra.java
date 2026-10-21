package es.gastos.clasificacion.aplicacion.siembra;

import es.gastos.kernel.dominio.UsuarioId;

/**
 * Puerto de salida: marca, una sola vez por usuario, que su catálogo inicial ya se sembró. Una vez
 * sembrado el catálogo es del usuario: aunque borre las entradas iniciales, no vuelven a aparecer.
 */
public interface RegistroSiembra {

	/**
	 * Marca al usuario como sembrado; devuelve {@code true} solo la primera vez. Con peticiones
	 * concurrentes, solo una obtiene {@code true} (la otra espera al commit de la primera).
	 */
	boolean marcar(UsuarioId usuario);
}
