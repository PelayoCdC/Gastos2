package es.gastos.consultas.infraestructura.persistencia;

import es.gastos.consultas.aplicacion.informes.LecturaApuntes;
import es.gastos.consultas.dominio.informes.Apunte;
import es.gastos.consultas.dominio.informes.Periodo;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carga los movimientos de un usuario para los informes con dos consultas: las transacciones (con
 * cuenta y categoría) y sus etiquetas, ambas con el mismo filtro y sin listas {@code IN} enormes.
 */
@Repository
class LecturaApuntesJdbc implements LecturaApuntes {

	private final JdbcClient jdbc;

	LecturaApuntesJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Apunte> de(UsuarioId usuario, Periodo periodo) {
		Map<String, Object> parametros = new HashMap<>();
		String condiciones = condiciones(usuario, periodo, parametros);

		Map<UUID, List<Apunte.Etiqueta>> etiquetas = new HashMap<>();
		this.jdbc.sql("""
						SELECT te.transaccion_id, e.id, e.nombre, e.categoria_id
						FROM transaccion_etiqueta_vista te
						JOIN etiqueta_vista e ON e.id = te.etiqueta_id AND NOT e.eliminada
						JOIN transaccion_vista t ON t.id = te.transaccion_id""" + condiciones + " ORDER BY te.transaccion_id, te.posicion")
				.params(parametros)
				.query(rs -> {
					etiquetas.computeIfAbsent(rs.getObject("transaccion_id", UUID.class), k -> new ArrayList<>())
							.add(new Apunte.Etiqueta(rs.getObject("id", UUID.class), rs.getString("nombre"),
									rs.getObject("categoria_id", UUID.class)));
				});

		return this.jdbc.sql("""
						SELECT t.id, t.fecha_contable, t.concepto, t.clave_concepto, t.importe, t.saldo, t.orden,
						       t.cuenta_id, c.nombre AS cuenta_nombre, t.categoria_id, cat.nombre AS categoria_nombre, cat.color AS categoria_color
						FROM transaccion_vista t
						LEFT JOIN cuenta_vista c ON c.id = t.cuenta_id
						LEFT JOIN categoria_vista cat ON cat.id = t.categoria_id AND NOT cat.eliminada""" + condiciones
						+ " ORDER BY t.fecha_contable, t.orden, t.id")
				.params(parametros)
				.query((rs, n) -> {
					UUID id = rs.getObject("id", UUID.class);
					UUID cuenta = rs.getObject("cuenta_id", UUID.class);
					UUID categoria = rs.getObject("categoria_id", UUID.class);
					return new Apunte(id, rs.getObject("fecha_contable", LocalDate.class), rs.getString("concepto"),
							rs.getString("clave_concepto"), rs.getBigDecimal("importe"), rs.getBigDecimal("saldo"), rs.getInt("orden"),
							cuenta == null ? null : new Apunte.Referencia(cuenta, rs.getString("cuenta_nombre")),
							categoria == null || rs.getString("categoria_nombre") == null ? null
									: new Apunte.Categoria(categoria, rs.getString("categoria_nombre"), rs.getString("categoria_color")),
							etiquetas.getOrDefault(id, List.of()));
				})
				.list();
	}

	private static String condiciones(UsuarioId usuario, Periodo periodo, Map<String, Object> parametros) {
		StringBuilder sql = new StringBuilder(" WHERE t.usuario_id = :usuario AND t.registrada AND NOT t.eliminada");
		parametros.put("usuario", usuario.valor());
		if (periodo.desde() != null) {
			sql.append(" AND t.fecha_contable >= :desde");
			parametros.put("desde", periodo.desde());
		}
		if (periodo.hasta() != null) {
			sql.append(" AND t.fecha_contable <= :hasta");
			parametros.put("hasta", periodo.hasta());
		}
		return sql.toString();
	}
}
