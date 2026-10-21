package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/** Se ha borrado una transacción concreta. Consumidores: clasificacion, consultas. */
public record TransaccionEliminada(UUID transaccionId, UUID documentoId) implements EventoIntegracion {

	public static final String TIPO = "TransaccionEliminada";
}
