/**
 * "Leer lo que acabas de escribir" (docs/adr/0011). Cada comando devuelve hasta qué evento llega su
 * cambio ({@code Gastos-Secuencia}); el cliente lo reenvía en {@code Gastos-Esperar} y consultas
 * compara esas {@code SecuenciasEsperadas} con la posición de sus proyecciones antes de responder.
 */
package es.gastos.consultas.dominio.consistencia;
