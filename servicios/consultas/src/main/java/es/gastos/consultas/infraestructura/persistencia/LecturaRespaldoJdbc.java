package es.gastos.consultas.infraestructura.persistencia;

import es.gastos.consultas.aplicacion.exportacion.LecturaRespaldo;
import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta el respaldo v1 desde las proyecciones, con lo vivo del usuario. Un saldo desconocido se escribe
 * como 0, como hacía el back antiguo: la app móvil espera siempre un número (al restaurarlo, el plan de
 * movimientos vuelve a reconocer esos 0 como "sin saldo", docs/adr/0020).
 */
@Repository
class LecturaRespaldoJdbc implements LecturaRespaldo {

	private static final TypeReference<List<String>> LISTA_TEXTOS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper json;

	LecturaRespaldoJdbc(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public RespaldoV1 de(UsuarioId usuario, LocalDate generadoEn) {
		UUID id = usuario.valor();

		List<RespaldoV1.Cuenta> cuentas = this.jdbc.sql("""
						SELECT id, nombre, banco, descripcion FROM cuenta_vista WHERE usuario_id = :usuario AND NOT eliminada ORDER BY nombre""")
				.param("usuario", id)
				.query((rs, n) -> new RespaldoV1.Cuenta(texto(rs.getObject("id", UUID.class)), rs.getString("nombre"), rs.getString("banco"),
						rs.getString("descripcion")))
				.list();

		List<RespaldoV1.Categoria> categorias = this.jdbc.sql("""
						SELECT id, nombre, color, descripcion, ocurrencias FROM categoria_vista
						WHERE usuario_id = :usuario AND NOT eliminada ORDER BY prioridad, nombre""")
				.param("usuario", id)
				.query((rs, n) -> new RespaldoV1.Categoria(texto(rs.getObject("id", UUID.class)), rs.getString("nombre"), rs.getString("color"),
						rs.getString("descripcion"), this.ocurrencias(rs.getString("ocurrencias"))))
				.list();

		List<RespaldoV1.Etiqueta> etiquetas = this.jdbc.sql("""
						SELECT id, nombre, descripcion, ocurrencias, categoria_id FROM etiqueta_vista
						WHERE usuario_id = :usuario AND NOT eliminada ORDER BY prioridad, nombre""")
				.param("usuario", id)
				.query((rs, n) -> new RespaldoV1.Etiqueta(texto(rs.getObject("id", UUID.class)), rs.getString("nombre"),
						rs.getString("descripcion"), this.ocurrencias(rs.getString("ocurrencias")), texto(rs.getObject("categoria_id", UUID.class))))
				.list();

		List<RespaldoV1.Configuracion> formatos = this.jdbc.sql("""
						SELECT id, nombre, fila_inicio, columna_concepto, columna_importe, columna_fecha_contable, columna_fecha_valor,
						       columna_saldo, formato_fecha
						FROM formato_vista WHERE usuario_id = :usuario AND NOT eliminado ORDER BY nombre""")
				.param("usuario", id)
				.query((rs, n) -> new RespaldoV1.Configuracion(texto(rs.getObject("id", UUID.class)), rs.getString("nombre"),
						rs.getObject("fila_inicio", Integer.class), rs.getObject("columna_concepto", Integer.class),
						rs.getObject("columna_importe", Integer.class), rs.getObject("columna_fecha_contable", Integer.class),
						rs.getObject("columna_fecha_valor", Integer.class), rs.getObject("columna_saldo", Integer.class), rs.getString("formato_fecha")))
				.list();

		List<RespaldoV1.Documento> documentos = this.jdbc.sql("""
						SELECT id, nombre, nombre_archivo, fecha, cuenta_id FROM documento_vista
						WHERE usuario_id = :usuario AND NOT eliminado ORDER BY fecha, nombre""")
				.param("usuario", id)
				.query((rs, n) -> new RespaldoV1.Documento(texto(rs.getObject("id", UUID.class)), rs.getString("nombre"),
						rs.getString("nombre_archivo"), texto(rs.getObject("fecha", LocalDate.class)), texto(rs.getObject("cuenta_id", UUID.class))))
				.list();

		Map<UUID, List<String>> etiquetasDe = new HashMap<>();
		this.jdbc.sql("""
						SELECT te.transaccion_id, te.etiqueta_id FROM transaccion_etiqueta_vista te
						JOIN transaccion_vista t ON t.id = te.transaccion_id
						WHERE t.usuario_id = :usuario AND t.registrada AND NOT t.eliminada
						ORDER BY te.transaccion_id, te.posicion""")
				.param("usuario", id)
				.query(rs -> {
					etiquetasDe.computeIfAbsent(rs.getObject("transaccion_id", UUID.class), k -> new ArrayList<>())
							.add(rs.getObject("etiqueta_id", UUID.class).toString());
				});

		List<RespaldoV1.Transaccion> transacciones = this.jdbc.sql("""
						SELECT id, concepto, nota, importe, saldo, orden, orden_documento, fecha_contable, fecha_valor, cuenta_id, documento_id,
						       categoria_id
						FROM transaccion_vista WHERE usuario_id = :usuario AND registrada AND NOT eliminada
						ORDER BY fecha_contable, orden, id""")
				.param("usuario", id)
				.query((rs, n) -> {
					UUID transaccion = rs.getObject("id", UUID.class);
					BigDecimal saldo = rs.getBigDecimal("saldo");
					return new RespaldoV1.Transaccion(transaccion.toString(), rs.getString("concepto"), rs.getString("nota"),
							rs.getBigDecimal("importe"), saldo == null ? BigDecimal.ZERO : saldo, rs.getInt("orden"), rs.getInt("orden_documento"),
							texto(rs.getObject("fecha_contable", LocalDate.class)), texto(rs.getObject("fecha_valor", LocalDate.class)),
							texto(rs.getObject("cuenta_id", UUID.class)), texto(rs.getObject("documento_id", UUID.class)),
							texto(rs.getObject("categoria_id", UUID.class)), etiquetasDe.getOrDefault(transaccion, List.of()));
				})
				.list();

		return new RespaldoV1(1, generadoEn.toString(), cuentas, categorias, etiquetas, formatos, documentos, transacciones);
	}

	private List<String> ocurrencias(String guardadas) {
		return guardadas == null ? List.of() : this.json.readValue(guardadas, LISTA_TEXTOS);
	}

	private static String texto(Object valor) {
		return valor == null ? null : valor.toString();
	}
}
