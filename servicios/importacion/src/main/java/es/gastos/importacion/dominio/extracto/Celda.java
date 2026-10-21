package es.gastos.importacion.dominio.extracto;

import java.time.LocalDate;

/**
 * Una celda del extracto. {@code texto} es lo que ve el usuario en su hoja de cálculo (vacío si no
 * hay nada); {@code numero} y {@code fecha} solo vienen cuando el formato del fichero los distingue
 * (una celda numérica o de fecha de Excel). En un CSV todo es texto.
 */
public record Celda(String texto, Double numero, LocalDate fecha) {

	public static final Celda VACIA = new Celda("", null, null);

	public Celda {
		texto = texto == null ? "" : texto;
	}

	public static Celda texto(String texto) {
		return new Celda(texto, null, null);
	}

	public boolean vacia() {
		return this.texto.isBlank() && this.numero == null && this.fecha == null;
	}
}
