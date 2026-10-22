package es.gastos.importacion.dominio.respaldo;

import es.gastos.contratos.respaldo.v1.RespaldoV1;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un respaldo v1 ya leído: ids y fechas tipados, y solo las transacciones que se pueden restaurar (con
 * id, fecha contable e importe). Es lo que importacion reparte a los servicios dueños de cada parte.
 */
public record RespaldoTraducido(List<Cuenta> cuentas, List<Documento> documentos, List<Transaccion> transacciones,
		List<Categoria> categorias, List<Etiqueta> etiquetas, List<RespaldoV1.Configuracion> configuraciones, List<String> anomalias) {

	public record Cuenta(UUID id, String nombre, String banco, String descripcion) {
	}

	public record Documento(UUID id, String nombre, String nombreArchivo, LocalDate fecha, UUID cuentaId) {
	}

	public record Transaccion(UUID id, String concepto, String nota, BigDecimal importe, BigDecimal saldo, int orden, int ordenDocumento,
			LocalDate fechaContable, LocalDate fechaValor, UUID cuentaId, UUID documentoId, UUID categoriaId, List<UUID> etiquetaIds) {
	}

	public record Categoria(UUID id, String nombre, String color, String descripcion, List<String> ocurrencias) {
	}

	public record Etiqueta(UUID id, String nombre, String descripcion, List<String> ocurrencias, UUID categoriaId) {
	}
}
