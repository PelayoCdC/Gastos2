package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** El usuario ha corregido los hechos de una transacción (estado completo de lo editable). Consumidores: clasificacion, consultas. */
public record TransaccionCorregida(
		UUID transaccionId,
		UUID cuentaId,
		LocalDate fechaContable,
		LocalDate fechaValor,
		String concepto,
		String nota,
		BigDecimal importe) implements EventoIntegracion {

	public static final String TIPO = "TransaccionCorregida";
}
