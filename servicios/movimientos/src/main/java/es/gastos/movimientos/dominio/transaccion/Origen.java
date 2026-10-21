package es.gastos.movimientos.dominio.transaccion;

/** De dónde viene una transacción. Clasificación solo aplica sus reglas automáticas a las importadas. */
public enum Origen {
	IMPORTACION,
	MANUAL,
	MIGRACION
}
