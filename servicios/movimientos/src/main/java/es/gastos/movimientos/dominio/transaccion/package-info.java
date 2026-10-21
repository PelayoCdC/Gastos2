/**
 * Agregado {@code Transaccion}: un movimiento bancario (hechos: fechas, concepto, importe, saldo).
 *
 * <p>No sabe nada de categorías ni etiquetas: la clasificación vive en otro contexto
 * (docs/adr/0002). Invariantes: si viene de un documento, su cuenta es la del documento y solo
 * cambia con él; su clave de duplicado se fija al importarla con los hechos originales del banco.
 */
package es.gastos.movimientos.dominio.transaccion;
