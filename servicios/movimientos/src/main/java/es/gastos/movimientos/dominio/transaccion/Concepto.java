package es.gastos.movimientos.dominio.transaccion;

import es.gastos.kernel.dominio.ExcepcionDominio;

/** Texto del banco que describe el movimiento. Puede estar vacío (hay bancos que lo dejan en blanco). */
public record Concepto(String valor) {

	public static final int LONGITUD_MAXIMA = 500;

	public Concepto {
		valor = valor == null ? "" : valor.strip();
		if (valor.length() > LONGITUD_MAXIMA) {
			throw new ExcepcionDominio("CONCEPTO_INVALIDO", "El concepto admite como mucho " + LONGITUD_MAXIMA + " caracteres");
		}
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
