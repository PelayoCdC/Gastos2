package es.gastos.contratos.importacion.v1;

import es.gastos.contratos.EventoIntegracion;

/**
 * Se han sustituido los formatos de banco del usuario por los de un respaldo (docs/adr/0020). Evento de
 * conjunto: los consumidores olvidan los formatos que tenían del usuario y después llegan los
 * {@code FormatoBancoGuardado} restaurados. {@code agregadoId} es el usuario. Consumidores: consultas.
 */
public record FormatosRestaurados(int formatos) implements EventoIntegracion {

	public static final String TIPO = "FormatosRestaurados";
}
