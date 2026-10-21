package es.gastos.contratos.clasificacion.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/**
 * Una etiqueta se ha borrado. Evento de conjunto: se quita de todas las transacciones que la
 * tenían (no llega un evento por transacción). Consumidores: consultas.
 */
public record EtiquetaEliminada(UUID etiquetaId) implements EventoIntegracion {

	public static final String TIPO = "EtiquetaEliminada";
}
