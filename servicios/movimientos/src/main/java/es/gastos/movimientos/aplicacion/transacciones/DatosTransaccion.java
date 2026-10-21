package es.gastos.movimientos.aplicacion.transacciones;

import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import es.gastos.movimientos.dominio.transaccion.Transaccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Hechos de una transacción tal y como se devuelven (sin clasificación: eso es de otro contexto). */
public record DatosTransaccion(UUID id, UUID cuentaId, UUID documentoId, LocalDate fechaContable, LocalDate fechaValor,
		String concepto, String nota, BigDecimal importe, BigDecimal saldo, int orden, int ordenDocumento, String origen) {

	public static DatosTransaccion de(Transaccion transaccion) {
		Transaccion.Datos d = transaccion.datos();
		return new DatosTransaccion(d.id().valor(),
				d.cuentaId() == null ? null : d.cuentaId().valor(),
				d.documentoId() == null ? null : d.documentoId().valor(),
				d.fechaContable(), d.fechaValor(), d.concepto().valor(), d.nota(), d.importe().valor(),
				d.saldo() == null ? null : d.saldo().valor(), d.orden(), d.ordenDocumento(), d.origen().name());
	}

	/** Atajo para comparar ids opcionales sin repetir comprobaciones de nulos. */
	static CuentaId cuenta(UUID id) {
		return id == null ? null : new CuentaId(id);
	}

	static DocumentoId documento(UUID id) {
		return id == null ? null : new DocumentoId(id);
	}
}
