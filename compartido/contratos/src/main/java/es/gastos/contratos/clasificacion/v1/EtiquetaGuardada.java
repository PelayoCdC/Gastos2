package es.gastos.contratos.clasificacion.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.List;
import java.util.UUID;

/**
 * Una etiqueta se ha creado o modificado (estado completo). {@code categoriaId} es la categoría que
 * aplica en cascada, o nulo. Consumidores: consultas.
 */
public record EtiquetaGuardada(UUID etiquetaId, String nombre, String descripcion, List<String> ocurrencias, UUID categoriaId,
		int prioridad) implements EventoIntegracion {

	public static final String TIPO = "EtiquetaGuardada";
}
