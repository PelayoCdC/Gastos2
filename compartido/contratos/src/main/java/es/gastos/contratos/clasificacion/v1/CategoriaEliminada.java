package es.gastos.contratos.clasificacion.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/**
 * Una categoría se ha borrado. Evento de conjunto: las transacciones que la tenían quedan sin
 * categoría (no llega un evento por transacción); las etiquetas que la usaban en cascada llegan
 * antes como {@code EtiquetaGuardada} sin categoría. Consumidores: consultas.
 */
public record CategoriaEliminada(UUID categoriaId) implements EventoIntegracion {

	public static final String TIPO = "CategoriaEliminada";
}
