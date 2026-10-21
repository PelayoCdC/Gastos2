package es.gastos.contratos.clasificacion.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.List;
import java.util.UUID;

/**
 * La clasificación de una transacción ha cambiado (estado completo: categoría y etiquetas en orden).
 *
 * @param motivo MANUAL, AUTOMATICA, PROPAGACION, MASIVA, REINICIO o MIGRACION
 * Consumidores: consultas.
 */
public record TransaccionClasificada(UUID transaccionId, UUID categoriaId, List<UUID> etiquetaIds, String motivo)
		implements EventoIntegracion {

	public static final String TIPO = "TransaccionClasificada";
}
