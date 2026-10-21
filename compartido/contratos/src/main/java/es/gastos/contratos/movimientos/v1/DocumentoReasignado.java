package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/**
 * Un documento (y <b>todas sus transacciones</b>) ha pasado a otra cuenta. Evento de conjunto: los
 * consumidores actualizan las transacciones del documento, no llega un evento por transacción.
 * Consumidores: consultas.
 */
public record DocumentoReasignado(UUID documentoId, UUID cuentaIdAnterior, UUID cuentaIdNueva) implements EventoIntegracion {

	public static final String TIPO = "DocumentoReasignado";
}
