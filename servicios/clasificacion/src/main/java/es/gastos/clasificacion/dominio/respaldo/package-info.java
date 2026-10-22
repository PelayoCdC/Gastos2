/**
 * Restauración de un respaldo (docs/adr/0020): {@code PlanRestauracion} traduce el catálogo y las
 * clasificaciones de un respaldo v1 a categorías, etiquetas y clasificaciones válidas, conservando los
 * ids y explicando en {@code anomalias} lo que ha corregido. Es puro: sirve para simular y para sustituir.
 *
 * <p>La prioridad sale de la posición en el respaldo (× 10, para dejar hueco a reordenar, docs/adr/0014):
 * el back antiguo no la tenía y su "primera coincidencia" dependía del orden en que la base de datos
 * devolvía las filas, que es justo el orden en que las exportó.
 */
package es.gastos.clasificacion.dominio.respaldo;
