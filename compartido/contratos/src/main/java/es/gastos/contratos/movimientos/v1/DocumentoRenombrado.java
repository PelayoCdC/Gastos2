package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/** El usuario ha cambiado el nombre de un documento. Consumidores: consultas. */
public record DocumentoRenombrado(UUID documentoId, String nombre) implements EventoIntegracion {

	public static final String TIPO = "DocumentoRenombrado";
}
