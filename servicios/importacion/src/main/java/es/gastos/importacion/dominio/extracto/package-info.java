/**
 * Interpretación de extractos: el corazón de la capa anticorrupción (docs/adr/0003).
 *
 * <p>Una {@code Hoja} es el fichero del banco ya leído, sin depender de Excel ni de CSV (cada celda
 * trae su texto y, si el formato lo distingue, su número o su fecha). {@code InterpreteExtracto}
 * la traduce, con un {@code FormatoBanco}, a líneas limpias en el lenguaje de movimientos y a errores
 * por fila. Todo es Java puro y se prueba sin ficheros.
 */
package es.gastos.importacion.dominio.extracto;
