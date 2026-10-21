package es.gastos.movimientos.aplicacion.cuentas;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuenta tal y como se lista: con el saldo del último movimiento que lo trae (fecha contable más
 * reciente y, a igual fecha, mayor orden) y cuántas transacciones tiene. {@code saldo} es nulo si
 * ningún movimiento de la cuenta trae saldo.
 */
public record ResumenCuenta(UUID id, String nombre, String banco, String descripcion, BigDecimal saldo,
		long numeroTransacciones) {
}
