package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

/**
 * Se han sustituido <b>todos</b> los movimientos del usuario por los de un respaldo (docs/adr/0020).
 * Evento de conjunto: los consumidores olvidan las cuentas, documentos y transacciones que tenían del
 * usuario, y a continuación llegan los {@code CuentaGuardada}, {@code DocumentoRegistrado} y
 * {@code TransaccionRegistrada} (origen MIGRACION) de lo restaurado, que pueden reutilizar los mismos ids.
 * {@code agregadoId} es el usuario. Consumidores: consultas (clasificacion lo ignora: restaura lo suyo aparte).
 */
public record MovimientosRestaurados(int cuentas, int documentos, int transacciones) implements EventoIntegracion {

	public static final String TIPO = "MovimientosRestaurados";
}
