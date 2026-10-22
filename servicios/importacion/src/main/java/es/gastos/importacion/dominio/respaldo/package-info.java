/**
 * Respaldos v1 (docs/adr/0020): el JSON completo de un usuario que generan el back antiguo
 * ({@code GET /migracion/exportar}), la app móvil y consultas ({@code GET /exportacion/v1}).
 *
 * <p>El formato en sí ({@code RespaldoV1}) está en {@code compartido/contratos}: lo leen importacion y el
 * móvil y lo escriben consultas, el back antiguo y el móvil. Tiene ids y fechas como texto y no se le
 * pide nada. {@code TraductorRespaldoV1} es la capa anticorrupción que lo convierte en
 * {@code RespaldoTraducido} (ids y fechas tipados) y explica en anomalías lo que no ha podido leer;
 * {@code PlanFormatos} saca de él los formatos de banco de este contexto.
 */
package es.gastos.importacion.dominio.respaldo;
