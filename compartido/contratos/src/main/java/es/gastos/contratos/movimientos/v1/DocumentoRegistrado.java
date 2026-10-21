package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Se ha importado un extracto y existe su documento. Se publica antes que las
 * {@code TransaccionRegistrada} de sus filas. Consumidores: consultas.
 */
public record DocumentoRegistrado(UUID documentoId, UUID cuentaId, String nombre, String nombreArchivo, LocalDate fecha)
		implements EventoIntegracion {

	public static final String TIPO = "DocumentoRegistrado";
}
