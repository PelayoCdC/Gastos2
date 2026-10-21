package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/**
 * Una cuenta se ha eliminado. Si tenía movimientos (borrado en cascada explícito), antes se publican
 * los {@code DocumentoEliminado} y {@code TransaccionEliminada} correspondientes. Consumidores: consultas.
 */
public record CuentaEliminada(UUID cuentaId) implements EventoIntegracion {

	public static final String TIPO = "CuentaEliminada";
}
