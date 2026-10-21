package es.gastos.identidad.aplicacion;

import es.gastos.kernel.dominio.ExcepcionDominio;

/**
 * El email no existe o la contraseña no coincide. Ambos casos dan el mismo error (401) para no
 * revelar qué emails están registrados.
 */
public class CredencialesInvalidas extends ExcepcionDominio {

	public CredencialesInvalidas() {
		super("CREDENCIALES_INVALIDAS", "Email o contrasena incorrectos");
	}
}
