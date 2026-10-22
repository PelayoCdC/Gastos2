package es.gastos.contratos.respaldo.v1;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respaldo completo de un usuario, formato v1 (docs/adr/0020): el que escriben el back antiguo
 * ({@code GET /migracion/exportar}, {@code ExportacionTotalDto}), la app móvil y consultas
 * ({@code GET /exportacion/v1}), y el que leen importacion ({@code POST /respaldos}) y el móvil.
 * Los nombres de los campos no se pueden cambiar sin una v2. Los ids y las fechas van como texto (ISO).
 */
public record RespaldoV1(Integer version, String generadoEn, List<Cuenta> cuentas, List<Categoria> categorias, List<Etiqueta> etiquetas,
		List<Configuracion> configuracionesImportacion, List<Documento> documentos, List<Transaccion> transacciones) {

	public RespaldoV1 {
		cuentas = cuentas == null ? List.of() : cuentas;
		categorias = categorias == null ? List.of() : categorias;
		etiquetas = etiquetas == null ? List.of() : etiquetas;
		configuracionesImportacion = configuracionesImportacion == null ? List.of() : configuracionesImportacion;
		documentos = documentos == null ? List.of() : documentos;
		transacciones = transacciones == null ? List.of() : transacciones;
	}

	public record Cuenta(String id, String nombre, String banco, String descripcion) {
	}

	public record Categoria(String id, String nombre, String color, String descripcion, List<String> ocurrencias) {
	}

	public record Etiqueta(String id, String nombre, String descripcion, List<String> ocurrencias, String idCategoria) {
	}

	public record Configuracion(String id, String nombre, Integer filaInicio, Integer columnaConcepto, Integer columnaImporte,
			Integer columnaFechaContable, Integer columnaFechaValor, Integer columnaSaldo, String formatoFecha) {
	}

	public record Documento(String id, String nombre, String nombreArchivo, String fecha, String idCuenta) {
	}

	public record Transaccion(String id, String concepto, String nota, BigDecimal importe, BigDecimal saldo, Integer orden,
			Integer ordenDocumento, String fechaContable, String fechaValor, String idCuenta, String idDocumento, String idCategoria,
			List<String> idsEtiquetas) {
	}
}
