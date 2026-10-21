package es.gastos.identidad.dominio.usuario;

import es.gastos.kernel.dominio.ExcepcionDominio;

/** Nombre visible del usuario: obligatorio (en el back antiguo podía ser nulo) y de 1 a 100 caracteres. */
public record Nombre(String valor) {

	public static final int LONGITUD_MAXIMA = 100;

	public Nombre {
		if (valor == null || valor.isBlank()) {
			throw new ExcepcionDominio("NOMBRE_INVALIDO", "El nombre es obligatorio");
		}

		valor = valor.strip();

		if (valor.length() > LONGITUD_MAXIMA) {
			throw new ExcepcionDominio("NOMBRE_INVALIDO", "El nombre admite como mucho " + LONGITUD_MAXIMA + " caracteres");
		}
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
