package es.gastos.contratos.clasificacion.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.List;
import java.util.UUID;

/**
 * Una categoría se ha creado o modificado (estado completo, incluidas ocurrencias y prioridad para
 * que consultas pueda generar la exportación v1). Consumidores: consultas.
 */
public record CategoriaGuardada(UUID categoriaId, String nombre, String color, String descripcion, List<String> ocurrencias,
		int prioridad) implements EventoIntegracion {

	public static final String TIPO = "CategoriaGuardada";
}
