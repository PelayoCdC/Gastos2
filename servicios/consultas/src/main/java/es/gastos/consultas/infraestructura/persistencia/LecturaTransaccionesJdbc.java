package es.gastos.consultas.infraestructura.persistencia;

import es.gastos.consultas.aplicacion.consulta.FilaTransaccion;
import es.gastos.consultas.aplicacion.consulta.LecturaTransacciones;
import es.gastos.consultas.dominio.transacciones.FiltroTransacciones;
import es.gastos.consultas.dominio.transacciones.Ordenacion;
import es.gastos.kernel.aplicacion.Pagina;
import es.gastos.kernel.aplicacion.PeticionPagina;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La rejilla de transacciones sobre las proyecciones. El SQL se monta con los filtros presentes y
 * siempre con parámetros con nombre (nunca se concatena un valor del cliente); el orden sale de una
 * lista blanca ({@link Ordenacion.Campo}).
 */
@Repository
class LecturaTransaccionesJdbc implements LecturaTransacciones {

	private static final String SELECCION = """
			SELECT t.id, t.fecha_contable, t.fecha_valor, t.concepto, t.nota, t.importe, t.saldo, t.orden,
			       t.cuenta_id, c.nombre AS cuenta_nombre, t.documento_id, d.nombre AS documento_nombre,
			       t.categoria_id, cat.nombre AS categoria_nombre, cat.color AS categoria_color""";

	private static final String ORIGEN = """
			FROM transaccion_vista t
			LEFT JOIN cuenta_vista c ON c.id = t.cuenta_id
			LEFT JOIN documento_vista d ON d.id = t.documento_id
			LEFT JOIN categoria_vista cat ON cat.id = t.categoria_id AND NOT cat.eliminada""";

	/** Las etiquetas de una página se piden aparte y por bloques, para no multiplicar las filas con un JOIN. */
	private static final int BLOQUE_ETIQUETAS = 1000;

	private final JdbcClient jdbc;

	LecturaTransaccionesJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Pagina<FilaTransaccion> buscar(UsuarioId usuario, FiltroTransacciones filtro, Ordenacion ordenacion, PeticionPagina pagina) {
		Map<String, Object> parametros = new HashMap<>();
		String condiciones = condiciones(usuario, filtro, parametros);

		long total = this.jdbc.sql("SELECT COUNT(*) " + ORIGEN + condiciones).params(parametros).query(Long.class).single();
		parametros.put("tamano", pagina.tamano());
		parametros.put("desplazamiento", pagina.desplazamiento());
		List<FilaTransaccion> filas = this.jdbc.sql(SELECCION + " " + ORIGEN + condiciones + orden(ordenacion)
						+ " LIMIT :tamano OFFSET :desplazamiento")
				.params(parametros)
				.query(LecturaTransaccionesJdbc::fila)
				.list();
		return Pagina.de(this.conEtiquetas(filas), pagina, total);
	}

	@Override
	public List<FilaTransaccion> todas(UsuarioId usuario, FiltroTransacciones filtro, Ordenacion ordenacion, int limite) {
		Map<String, Object> parametros = new HashMap<>();
		String condiciones = condiciones(usuario, filtro, parametros);
		parametros.put("limite", limite);
		List<FilaTransaccion> filas = this.jdbc.sql(SELECCION + " " + ORIGEN + condiciones + orden(ordenacion) + " LIMIT :limite")
				.params(parametros)
				.query(LecturaTransaccionesJdbc::fila)
				.list();
		return this.conEtiquetas(filas);
	}

	@Override
	public Optional<FilaTransaccion> una(UsuarioId usuario, UUID id) {
		Map<String, Object> parametros = new HashMap<>();
		String condiciones = condiciones(usuario, FiltroTransacciones.NINGUNO, parametros) + " AND t.id = :id";
		parametros.put("id", id);
		return this.conEtiquetas(this.jdbc.sql(SELECCION + " " + ORIGEN + condiciones)
				.params(parametros)
				.query(LecturaTransaccionesJdbc::fila)
				.list()).stream().findFirst();
	}

	private static String condiciones(UsuarioId usuario, FiltroTransacciones filtro, Map<String, Object> parametros) {
		StringBuilder sql = new StringBuilder(" WHERE t.usuario_id = :usuario AND t.registrada AND NOT t.eliminada");
		parametros.put("usuario", usuario.valor());

		if (filtro.concepto() != null) {
			sql.append(" AND t.concepto_normalizado LIKE :concepto");
			parametros.put("concepto", "%" + escaparLike(filtro.conceptoNormalizado()) + "%");
		}
		anadir(sql, parametros, " AND t.fecha_contable >= :desde", "desde", filtro.desde());
		anadir(sql, parametros, " AND t.fecha_contable <= :hasta", "hasta", filtro.hasta());
		anadir(sql, parametros, " AND t.importe >= :importeMin", "importeMin", filtro.importeMin());
		anadir(sql, parametros, " AND t.importe <= :importeMax", "importeMax", filtro.importeMax());
		anadir(sql, parametros, " AND t.cuenta_id = :cuenta", "cuenta", filtro.cuentaId());
		anadir(sql, parametros, " AND t.documento_id = :documento", "documento", filtro.documentoId());

		if (filtro.filtraCategorias()) {
			List<String> opciones = new ArrayList<>();
			if (!filtro.categorias().isEmpty()) {
				opciones.add("t.categoria_id IN (:categorias)");
				parametros.put("categorias", List.copyOf(filtro.categorias()));
			}
			if (filtro.sinCategoria()) {
				opciones.add("t.categoria_id IS NULL");
			}
			sql.append(" AND (").append(String.join(" OR ", opciones)).append(")");
		}

		if (filtro.filtraEtiquetas()) {
			List<String> opciones = new ArrayList<>();
			if (!filtro.etiquetas().isEmpty()) {
				opciones.add("EXISTS (SELECT 1 FROM transaccion_etiqueta_vista te WHERE te.transaccion_id = t.id AND te.etiqueta_id IN (:etiquetas))");
				parametros.put("etiquetas", List.copyOf(filtro.etiquetas()));
			}
			if (filtro.sinEtiqueta()) {
				opciones.add("NOT EXISTS (SELECT 1 FROM transaccion_etiqueta_vista te WHERE te.transaccion_id = t.id)");
			}
			sql.append(" AND (").append(String.join(" OR ", opciones)).append(")");
		}
		return sql.toString();
	}

	private static void anadir(StringBuilder sql, Map<String, Object> parametros, String condicion, String nombre, Object valor) {
		if (valor != null) {
			sql.append(condicion);
			parametros.put(nombre, valor);
		}
	}

	/** {@code %} y {@code _} del texto buscado son literales, no comodines. */
	private static String escaparLike(String texto) {
		return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static String orden(Ordenacion ordenacion) {
		String columna = switch (ordenacion.campo()) {
			case FECHA_CONTABLE -> "t.fecha_contable";
			case FECHA_VALOR -> "t.fecha_valor";
			case IMPORTE -> "t.importe";
			case CONCEPTO -> "t.concepto";
			case CUENTA -> "c.nombre";
			case CATEGORIA -> "cat.nombre";
		};
		String direccion = ordenacion.ascendente() ? " ASC" : " DESC";
		return " ORDER BY " + columna + direccion + ", t.orden" + direccion + ", t.id" + direccion;
	}

	private List<FilaTransaccion> conEtiquetas(List<FilaTransaccion> filas) {
		if (filas.isEmpty()) {
			return filas;
		}
		Map<UUID, List<FilaTransaccion.Referencia>> etiquetas = new HashMap<>();
		List<UUID> ids = filas.stream().map(FilaTransaccion::id).toList();
		for (int desde = 0; desde < ids.size(); desde += BLOQUE_ETIQUETAS) {
			this.jdbc.sql("""
							SELECT te.transaccion_id, e.id, e.nombre
							FROM transaccion_etiqueta_vista te JOIN etiqueta_vista e ON e.id = te.etiqueta_id AND NOT e.eliminada
							WHERE te.transaccion_id IN (:ids)
							ORDER BY te.transaccion_id, te.posicion""")
					.param("ids", ids.subList(desde, Math.min(desde + BLOQUE_ETIQUETAS, ids.size())))
					.query(rs -> {
						etiquetas.computeIfAbsent(rs.getObject("transaccion_id", UUID.class), k -> new ArrayList<>())
								.add(new FilaTransaccion.Referencia(rs.getObject("id", UUID.class), rs.getString("nombre")));
					});
		}
		return filas.stream().map(f -> f.conEtiquetas(etiquetas.getOrDefault(f.id(), List.of()))).toList();
	}

	private static FilaTransaccion fila(ResultSet rs, int numero) throws SQLException {
		UUID cuenta = rs.getObject("cuenta_id", UUID.class);
		UUID documento = rs.getObject("documento_id", UUID.class);
		UUID categoria = rs.getObject("categoria_id", UUID.class);
		return new FilaTransaccion(rs.getObject("id", UUID.class), rs.getObject("fecha_contable", LocalDate.class),
				rs.getObject("fecha_valor", LocalDate.class), rs.getString("concepto"), rs.getString("nota"),
				rs.getBigDecimal("importe"), rs.getBigDecimal("saldo"), rs.getInt("orden"),
				cuenta == null ? null : new FilaTransaccion.Referencia(cuenta, rs.getString("cuenta_nombre")),
				documento == null ? null : new FilaTransaccion.Referencia(documento, rs.getString("documento_nombre")),
				categoria == null || rs.getString("categoria_nombre") == null ? null
						: new FilaTransaccion.Categoria(categoria, rs.getString("categoria_nombre"), rs.getString("categoria_color")),
				List.of());
	}
}
