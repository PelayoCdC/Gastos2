/**
 * Kernel compartido de dominio: conceptos con la misma semántica en todos los contextos.
 *
 * <p>Regla de admisión: algo solo entra aquí si al menos dos contextos necesitan exactamente la
 * misma semántica (no basta con que se parezca). Todo lo demás se duplica a propósito en cada
 * contexto, porque compartir modelo acopla los servicios. Ver {@code docs/dominio/mapa-de-contextos.md}.
 */
package es.gastos.kernel.dominio;
