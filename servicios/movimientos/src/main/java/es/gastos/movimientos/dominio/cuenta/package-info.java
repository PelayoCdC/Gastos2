/**
 * Agregado {@code Cuenta}: una cuenta bancaria del usuario.
 *
 * <p>Invariantes: el nombre es único por usuario sobre su forma normalizada (docs/adr/0013). Una
 * cuenta con movimientos no se borra sin pedirlo explícitamente (en el back antiguo daba un 500
 * por la clave foránea). El saldo no es un atributo: es el del último movimiento con saldo, y lo
 * calcula una consulta, no el agregado.
 */
package es.gastos.movimientos.dominio.cuenta;
