/**
 * Cómo se pide la rejilla de transacciones: {@code FiltroTransacciones} (qué filas) y
 * {@code Ordenacion} (en qué orden). No hay agregados: consultas no tiene comandos de negocio y su
 * modelo es una proyección de los eventos de otros contextos (docs/adr/0010).
 *
 * <p>Las reglas de los filtros son las del back antiguo, que el usuario ya conoce: dentro de un mismo
 * grupo (categorías o etiquetas) las opciones se suman con O, y los grupos entre sí se combinan con Y.
 */
package es.gastos.consultas.dominio.transacciones;
