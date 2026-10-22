/**
 * Restauración de un respaldo (docs/adr/0020): {@code PlanRestauracion} traduce los datos de un
 * respaldo v1 (del back antiguo o del móvil) a cuentas, documentos y transacciones válidos, y explica en
 * {@code anomalias} todo lo que ha tenido que corregir o descartar. Es puro: el mismo plan sirve para
 * simular (solo el informe) y para sustituir de verdad.
 *
 * <p>Las reglas salen de cómo eran los datos del back antiguo: nombres repetidos que ahora deben ser
 * únicos, documentos sin cuenta o sin fecha, y saldos a 0 que en realidad significaban "el banco no lo
 * dio" (el {@code float} no admitía nulos).
 */
package es.gastos.movimientos.dominio.respaldo;
