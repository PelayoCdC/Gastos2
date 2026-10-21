package es.gastos.contratos.importacion.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/**
 * Un formato de banco (cómo leer los extractos de un banco) se ha creado o modificado; estado completo.
 * Las columnas empiezan en 0 y {@code filaInicio} en 1. Consumidores: consultas (exportación v1).
 */
public record FormatoBancoGuardado(UUID formatoId, String nombre, int filaInicio, int columnaConcepto, int columnaImporte,
		int columnaFechaContable, Integer columnaFechaValor, Integer columnaSaldo, String formatoFecha) implements EventoIntegracion {

	public static final String TIPO = "FormatoBancoGuardado";
}
