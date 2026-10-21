package es.gastos.kernel.dominio;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalización de texto para comparar sin distinguir mayúsculas ni tildes.
 *
 * <p>En el back antiguo esta comparación dependía en parte de la collation de MariaDB
 * ({@code utf8mb4_uca1400_ai_ci}) y en parte de código Java, con resultados distintos según el
 * camino. Aquí es explícita y única para todo el sistema (ver {@code docs/adr/0013}).
 */
public final class Normalizador {

	private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}");

	// UNICODE_CHARACTER_CLASS: también colapsa el espacio duro (U+00A0) que algunos bancos usan
	private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

	private Normalizador() {
	}

	/**
	 * Quita tildes y diacríticos y pasa a minúsculas. <b>No recorta ni colapsa espacios</b>: las
	 * ocurrencias de clasificación dependen de ellos ({@code "bar "} no debe casar con
	 * "barcelona"). {@code null} se trata como texto vacío.
	 */
	public static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}

		String sinMarcas = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
		return sinMarcas.toLowerCase(Locale.ROOT);
	}

	/**
	 * Forma canónica de un nombre (de cuenta, categoría, etiqueta, formato) para garantizar su
	 * unicidad por usuario: "Alimentación", "alimentacion" y " ALIMENTACION " son el mismo nombre.
	 */
	public static String normalizarNombre(String texto) {
		return colapsarEspacios(normalizar(texto));
	}

	/** Sustituye cualquier secuencia de espacios por uno solo y recorta los extremos. */
	public static String colapsarEspacios(String texto) {
		if (texto == null) {
			return "";
		}

		return ESPACIOS.matcher(texto).replaceAll(" ").strip();
	}

	public static boolean esBlanco(String texto) {
		return texto == null || texto.isBlank();
	}
}
