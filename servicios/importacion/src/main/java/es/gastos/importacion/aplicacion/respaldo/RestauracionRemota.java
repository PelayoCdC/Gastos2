package es.gastos.importacion.aplicacion.respaldo;

import es.gastos.importacion.dominio.respaldo.RespaldoTraducido;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;

/**
 * Puertos de salida hacia los servicios dueños de cada parte de un respaldo (docs/adr/0020). Cada uno
 * sustituye lo suyo en su propia transacción o, con {@code simular}, solo informa de lo que haría.
 */
public final class RestauracionRemota {

	private RestauracionRemota() {
	}

	/** {@code PUT /internal/respaldos/v1} de movimientos: cuentas, documentos y transacciones. */
	public interface Movimientos {

		ResumenMovimientos restaurarMovimientos(UsuarioId usuario, RespaldoTraducido respaldo, boolean simular);
	}

	/** {@code PUT /internal/respaldos/v1} de clasificacion: catálogo y clasificación de cada transacción. */
	public interface Clasificacion {

		ResumenClasificacion restaurarClasificacion(UsuarioId usuario, RespaldoTraducido respaldo, boolean simular);
	}

	public record ResumenMovimientos(int cuentas, int documentos, int transacciones, List<String> anomalias) {
	}

	public record ResumenClasificacion(int categorias, int etiquetas, int clasificadas, List<String> anomalias) {
	}
}
