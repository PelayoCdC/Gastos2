/**
 * Informes y análisis de gasto: calculadores <b>puros</b> sobre los {@code Apunte}s de un usuario
 * (resumen mensual, evolución del saldo, gastos por categoría y por etiqueta, gasto diario, top de
 * conceptos, {@code DetectorRecurrentes} y {@code AgregadorBizum}).
 *
 * <p>Se calculan en memoria, como hacía el back antiguo con los análisis: un usuario tiene unos pocos
 * miles de movimientos y así toda la semántica se prueba sin base de datos. Diferencias con el
 * antiguo: el dinero es {@code BigDecimal} exacto (antes {@code double}), la fecha de "hoy" llega de
 * fuera (antes {@code LocalDate.now()}), los meses se ordenan como números (antes "10" iba antes que
 * "9") y los saldos desconocidos no cuentan (antes eran 0).
 *
 * <p>Convención de signos: un importe negativo es un gasto; los informes de gasto lo devuelven en
 * positivo.
 */
package es.gastos.consultas.dominio.informes;
