package es.gastos.movimientos.aplicacion.documentos;

import java.time.LocalDate;
import java.util.UUID;

/** Documento tal y como se lista, con su cuenta y el número de transacciones que conserva. */
public record ResumenDocumento(UUID id, String nombre, String nombreArchivo, LocalDate fecha, CuentaResumida cuenta,
		long numeroTransacciones) {

	/** {@code null} en documentos migrados sin cuenta. */
	public record CuentaResumida(UUID id, String nombre) {
	}
}
