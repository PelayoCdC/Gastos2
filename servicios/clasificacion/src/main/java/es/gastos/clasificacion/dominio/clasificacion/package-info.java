/**
 * Clasificación de cada transacción (agregado {@code ClasificacionTransaccion}): qué categoría y qué
 * etiquetas tiene y por qué (origen de cada asignación), junto con la copia mínima de la transacción
 * que hace falta para clasificarla (concepto y documento), alimentada por los eventos de movimientos.
 *
 * <p>Vive aquí y no en movimientos para que todas las operaciones que mezclan catálogo y asignaciones
 * (cascada, propagar, aplicar en bloque, reetiquetar, borrar una categoría) sean locales y atómicas
 * (docs/adr/0002).
 */
package es.gastos.clasificacion.dominio.clasificacion;
