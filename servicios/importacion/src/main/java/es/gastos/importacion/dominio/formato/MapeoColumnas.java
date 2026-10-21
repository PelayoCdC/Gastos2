package es.gastos.importacion.dominio.formato;

import es.gastos.kernel.dominio.ExcepcionDominio;

/**
 * Qué columna de la hoja (empezando en 0) contiene cada campo. Concepto, importe y fecha contable son
 * obligatorios; fecha valor y saldo, opcionales (nulos si el banco no los da).
 */
public record MapeoColumnas(Integer concepto, Integer importe, Integer fechaContable, Integer fechaValor, Integer saldo) {

	public MapeoColumnas {
		obligatoria(concepto, "concepto");
		obligatoria(importe, "importe");
		obligatoria(fechaContable, "fecha contable");
		opcional(fechaValor, "fecha valor");
		opcional(saldo, "saldo");
	}

	private static void obligatoria(Integer columna, String campo) {
		if (columna == null) {
			throw new ExcepcionDominio("MAPEO_INCOMPLETO", "Falta la columna de " + campo);
		}
		opcional(columna, campo);
	}

	private static void opcional(Integer columna, String campo) {
		if (columna != null && columna < 0) {
			throw new ExcepcionDominio("MAPEO_INVALIDO", "La columna de " + campo + " no puede ser negativa");
		}
	}
}
