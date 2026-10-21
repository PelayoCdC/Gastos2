package es.gastos.movimientos.dominio.comun;

import es.gastos.kernel.dominio.ExcepcionDominio;

/** Reglas comunes para los textos libres opcionales (descripciones, notas, banco). */
public final class Textos {

	private Textos() {
	}

	/** Recorta los extremos; un texto vacío se trata como ausente ({@code null}). */
	public static String opcional(String texto, int longitudMaxima, String codigo, String campo) {
		if (texto == null || texto.isBlank()) {
			return null;
		}

		String limpio = texto.strip();
		if (limpio.length() > longitudMaxima) {
			throw new ExcepcionDominio(codigo, campo + " admite como mucho " + longitudMaxima + " caracteres");
		}
		return limpio;
	}

	public static String obligatorio(String texto, int longitudMaxima, String codigo, String campo) {
		String limpio = opcional(texto, longitudMaxima, codigo, campo);
		if (limpio == null) {
			throw new ExcepcionDominio(codigo, campo + " es obligatorio");
		}
		return limpio;
	}
}
