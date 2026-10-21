package es.gastos.identidad.dominio.usuario;

import es.gastos.kernel.dominio.ExcepcionDominio;

import java.nio.charset.StandardCharsets;

/**
 * Contraseña tal y como la escribe el usuario. Solo vive en memoria durante el registro o el
 * inicio de sesión; nunca se guarda ni se registra en logs ({@link #toString()} la oculta).
 *
 * <p>Política: al menos {@value #LONGITUD_MINIMA} caracteres. El máximo de {@value #BYTES_MAXIMOS}
 * bytes no es una preferencia: BCrypt ignora lo que pasa de 72 bytes, así que una contraseña más
 * larga daría una falsa sensación de seguridad.
 */
public record ContrasenaEnClaro(String valor) {

	public static final int LONGITUD_MINIMA = 8;

	public static final int BYTES_MAXIMOS = 72;

	public ContrasenaEnClaro {
		if (valor == null || valor.length() < LONGITUD_MINIMA) {
			throw new ExcepcionDominio("CONTRASENA_INVALIDA",
					"La contrasena debe tener al menos " + LONGITUD_MINIMA + " caracteres");
		}

		if (valor.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS) {
			throw new ExcepcionDominio("CONTRASENA_INVALIDA", "La contrasena es demasiado larga");
		}
	}

	@Override
	public String toString() {
		return "********";
	}
}
