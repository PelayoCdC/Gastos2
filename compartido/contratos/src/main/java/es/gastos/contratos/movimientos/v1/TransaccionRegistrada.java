package es.gastos.contratos.movimientos.v1;

import es.gastos.contratos.EventoIntegracion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Se ha registrado una transacción (estado completo de sus hechos). Consumidores: clasificacion, consultas.
 *
 * @param origen IMPORTACION, MANUAL o MIGRACION; clasificacion solo aplica la clasificación automática
 *               a las de IMPORTACION
 */
public record TransaccionRegistrada(
		UUID transaccionId,
		String origen,
		UUID cuentaId,
		UUID documentoId,
		LocalDate fechaContable,
		LocalDate fechaValor,
		String concepto,
		String nota,
		BigDecimal importe,
		BigDecimal saldo,
		int orden,
		int ordenDocumento) implements EventoIntegracion {

	public static final String TIPO = "TransaccionRegistrada";
}
