/**
 * Agregado {@code FormatoBanco}: cómo leer los extractos de un banco (fila en la que empiezan los
 * datos, columna de cada campo y formato de fecha). Antes se llamaba "configuración de importación".
 *
 * <p>Invariantes: concepto, importe y fecha contable siempre tienen columna (en el back antiguo se
 * descubría al importar; ahora al guardar el formato), el formato de fecha es un patrón válido y el
 * nombre es único por usuario en su forma normalizada.
 */
package es.gastos.importacion.dominio.formato;
