package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/**
 * Un documento se ha borrado <b>junto con todas sus transacciones</b>. Evento de conjunto: los
 * consumidores borran (u olvidan) las transacciones con ese {@code documentoId}.
 * Consumidores: clasificacion, consultas.
 */
public record DocumentoEliminado(UUID documentoId) implements EventoIntegracion {

	public static final String TIPO = "DocumentoEliminado";
}
