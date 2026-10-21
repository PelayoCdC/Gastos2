package es.gastos.movimientos.dominio.documento;

import es.gastos.movimientos.dominio.comun.Textos;

/** Nombre visible de un documento (editable); por defecto, el del fichero importado. */
public record NombreDocumento(String valor) {

	public static final int LONGITUD_MAXIMA = 255;

	public NombreDocumento {
		valor = Textos.obligatorio(valor, LONGITUD_MAXIMA, "NOMBRE_INVALIDO", "El nombre del documento");
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
