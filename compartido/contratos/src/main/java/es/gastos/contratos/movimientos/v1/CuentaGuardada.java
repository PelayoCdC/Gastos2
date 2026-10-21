package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/** Una cuenta se ha creado o modificado (estado completo). Consumidores: consultas. */
public record CuentaGuardada(UUID cuentaId, String nombre, String banco, String descripcion) implements EventoIntegracion {

	public static final String TIPO = "CuentaGuardada";
}
