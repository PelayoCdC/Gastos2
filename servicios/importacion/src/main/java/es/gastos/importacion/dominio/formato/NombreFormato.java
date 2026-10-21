package es.gastos.importacion.dominio.formato;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Normalizador;

/** Nombre de un formato de banco ("Unicaja xlsx"): obligatorio, hasta 100 caracteres, único por usuario. */
public record NombreFormato(String valor) {

	public static final int LONGITUD_MAXIMA = 100;

	public NombreFormato {
		if (valor == null || valor.isBlank()) {
			throw new ExcepcionDominio("NOMBRE_INVALIDO", "El nombre del formato es obligatorio");
		}
		valor = valor.strip();
		if (valor.length() > LONGITUD_MAXIMA) {
			throw new ExcepcionDominio("NOMBRE_INVALIDO", "El nombre admite como mucho " + LONGITUD_MAXIMA + " caracteres");
		}
	}

	public String normalizado() {
		return Normalizador.normalizarNombre(this.valor);
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
