/**
 * Catálogo de clasificación de cada usuario: agregados {@code Categoria} y {@code Etiqueta}.
 *
 * <p>Las reglas que dan valor a la aplicación salen de aquí: cada entrada tiene <b>ocurrencias</b>
 * (textos que, si aparecen en el concepto, la aplican) y una <b>prioridad</b> explícita que decide
 * quién gana cuando varias coinciden (docs/adr/0014). Una etiqueta puede llevar una categoría que
 * aplica en <b>cascada</b>. Los nombres son únicos por usuario y tipo sobre su forma normalizada
 * (una categoría y una etiqueta sí pueden llamarse igual: "Transporte" es las dos cosas).
 */
package es.gastos.clasificacion.dominio.catalogo;
