/**
 * Agregado {@code Documento}: el registro de una importación de extracto (antes "documento" en el
 * back antiguo; el término se mantiene por continuidad).
 *
 * <p>Sus transacciones son agregados aparte, pero su ciclo de vida va unido al documento: reasignar
 * el documento a otra cuenta mueve todas sus transacciones y borrarlo las borra. Esas operaciones se
 * hacen por conjuntos (una sentencia SQL, un evento) dentro de la misma unidad de trabajo.
 */
package es.gastos.movimientos.dominio.documento;
