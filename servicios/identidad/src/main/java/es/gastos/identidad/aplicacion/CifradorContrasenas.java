package es.gastos.identidad.aplicacion;

import es.gastos.identidad.dominio.usuario.ContrasenaEnClaro;
import es.gastos.identidad.dominio.usuario.HashContrasena;

/** Puerto de salida: cálculo y comprobación de hashes de contraseña (adaptador BCrypt). */
public interface CifradorContrasenas {

	HashContrasena cifrar(ContrasenaEnClaro contrasena);

	boolean coincide(ContrasenaEnClaro contrasena, HashContrasena hash);

	/**
	 * Hace el mismo trabajo que una comprobación real sin tener un hash con el que comparar. Se usa
	 * cuando el email no existe, para que el tiempo de respuesta no revele qué emails están registrados.
	 */
	void simularComprobacion(ContrasenaEnClaro contrasena);
}
