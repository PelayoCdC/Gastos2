package es.gastos.contratos.importacion.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/** Un formato de banco se ha borrado. Consumidores: consultas. */
public record FormatoBancoEliminado(UUID formatoId) implements EventoIntegracion {

	public static final String TIPO = "FormatoBancoEliminado";
}
