package es.gastos.identidad.dominio.usuario;

import es.gastos.kernel.dominio.ExcepcionDominio;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Email del usuario en forma canónica: sin espacios en los extremos y en minúsculas.
 *
 * <p>La forma canónica es la que garantiza la unicidad: "Ana@Ejemplo.es" y "ana@ejemplo.es " son
 * el mismo usuario. En el back antiguo esto dependía de la collation de la base de datos.
 */
public record Email(String valor) {

	public static final int LONGITUD_MAXIMA = 254;

	// Validación deliberadamente sencilla: algo@algo.algo sin espacios; la prueba real es poder iniciar sesión
	private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public Email {
		if (valor == null || valor.isBlank()) {
			throw new ExcepcionDominio("EMAIL_INVALIDO", "El email es obligatorio");
		}

		valor = valor.strip().toLowerCase(Locale.ROOT);

		if (valor.length() > LONGITUD_MAXIMA || !FORMATO.matcher(valor).matches()) {
			throw new ExcepcionDominio("EMAIL_INVALIDO", "El email no tiene un formato valido");
		}
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
