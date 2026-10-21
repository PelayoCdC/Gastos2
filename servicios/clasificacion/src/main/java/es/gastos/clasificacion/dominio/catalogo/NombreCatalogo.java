package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Normalizador;

/** Nombre de una categoría o etiqueta: obligatorio, hasta 100 caracteres, único por usuario y tipo. */
public record NombreCatalogo(String valor) {

	public static final int LONGITUD_MAXIMA = 100;

	public NombreCatalogo {
		if (valor == null || valor.isBlank()) {
			throw new ExcepcionDominio("NOMBRE_INVALIDO", "El nombre es obligatorio");
		}
		valor = valor.strip();
		if (valor.length() > LONGITUD_MAXIMA) {
			throw new ExcepcionDominio("NOMBRE_INVALIDO", "El nombre admite como mucho " + LONGITUD_MAXIMA + " caracteres");
		}
	}

	/** Forma con la que se compara la unicidad: "Alimentación" y " alimentacion" son el mismo nombre. */
	public String normalizado() {
		return Normalizador.normalizarNombre(this.valor);
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
