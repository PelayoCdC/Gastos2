package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Normalizador;

/**
 * Texto que, si aparece en el concepto de una transacción, aplica una categoría o una etiqueta.
 *
 * <p>Se guarda <b>tal cual, con sus espacios en los extremos</b>: forman parte de la regla. El
 * catálogo por defecto usa {@code "bar "} para no casar con "barcelona" y {@code " ibi"} para no
 * casar con "exhibicion". El back antiguo los recortaba al editar una categoría y rompía esas reglas
 * sin que nadie lo notara. La comparación ignora mayúsculas y tildes ({@link Normalizador#normalizar}),
 * nunca los espacios.
 */
public record Ocurrencia(String texto) {

	public static final int LONGITUD_MAXIMA = 100;

	public Ocurrencia {
		if (texto == null || texto.isBlank()) {
			throw new ExcepcionDominio("OCURRENCIA_INVALIDA", "Una ocurrencia no puede estar vacia");
		}
		if (texto.length() > LONGITUD_MAXIMA) {
			throw new ExcepcionDominio("OCURRENCIA_INVALIDA", "Una ocurrencia admite como mucho " + LONGITUD_MAXIMA + " caracteres");
		}
	}

	public String normalizada() {
		return Normalizador.normalizar(this.texto);
	}

	/** @param conceptoNormalizado concepto ya pasado por {@link Normalizador#normalizar} */
	public boolean apareceEn(String conceptoNormalizado) {
		return conceptoNormalizado.contains(this.normalizada());
	}
}
