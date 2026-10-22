package es.gastos.movimientos.infraestructura.persistencia;

import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import es.gastos.movimientos.dominio.transaccion.ClaveDuplicado;
import es.gastos.movimientos.dominio.transaccion.Concepto;
import es.gastos.movimientos.dominio.transaccion.Origen;
import es.gastos.movimientos.dominio.transaccion.Transaccion;
import es.gastos.movimientos.dominio.transaccion.TransaccionId;
import es.gastos.movimientos.dominio.transaccion.TransaccionRepositorio;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class TransaccionRepositorioJdbc implements TransaccionRepositorio {

	static final String COLUMNAS = """
			id, usuario_id, cuenta_id, documento_id, fecha_contable, fecha_valor, concepto, nota, importe, saldo,
			orden, orden_documento, origen, clave_duplicado, version""";

	static final RowMapper<Transaccion> MAPEO = (rs, n) -> Transaccion.reconstituir(
			new UsuarioId(rs.getObject("usuario_id", UUID.class)),
			new Transaccion.Datos(
					new TransaccionId(rs.getObject("id", UUID.class)),
					Columnas.id(rs, "cuenta_id", CuentaId::new),
					Columnas.id(rs, "documento_id", DocumentoId::new),
					Columnas.fecha(rs, "fecha_contable"),
					Columnas.fecha(rs, "fecha_valor"),
					new Concepto(rs.getString("concepto")),
					rs.getString("nota"),
					Columnas.importe(rs, "importe"),
					Columnas.importe(rs, "saldo"),
					rs.getInt("orden"),
					rs.getInt("orden_documento"),
					Origen.valueOf(rs.getString("origen")),
					new ClaveDuplicado(rs.getString("clave_duplicado"))),
			rs.getLong("version"));

	private final JdbcClient jdbc;

	private final NamedParameterJdbcTemplate plantilla;

	TransaccionRepositorioJdbc(JdbcClient jdbc, NamedParameterJdbcTemplate plantilla) {
		this.jdbc = jdbc;
		this.plantilla = plantilla;
	}

	@Override
	public void guardar(Transaccion transaccion) {
		if (!transaccion.tieneCambios()) {
			return;
		}

		if (transaccion.esNuevo()) {
			this.jdbc.sql("INSERT INTO transaccion (" + COLUMNAS + ") VALUES (:id, :usuario, :cuenta, :documento, :fechaContable,"
							+ " :fechaValor, :concepto, :nota, :importe, :saldo, :orden, :ordenDocumento, :origen, :clave, :version)")
					.params(parametros(transaccion))
					.update();
			return;
		}

		int actualizadas = this.jdbc.sql("""
						UPDATE transaccion SET cuenta_id = :cuenta, fecha_contable = :fechaContable, fecha_valor = :fechaValor,
						       concepto = :concepto, nota = :nota, importe = :importe, clave_duplicado = :clave, version = :version
						WHERE id = :id AND version = :versionCargada
						""")
				.params(parametros(transaccion))
				.param("versionCargada", transaccion.versionCargada())
				.update();
		if (actualizadas == 0) {
			throw Conflicto.modificacionConcurrente("la transaccion", transaccion.id());
		}
	}

	static Map<String, Object> parametros(Transaccion transaccion) {
		Transaccion.Datos d = transaccion.datos();
		Map<String, Object> parametros = new HashMap<>();
		parametros.put("id", d.id().valor());
		parametros.put("usuario", transaccion.propietario().valor());
		parametros.put("cuenta", Columnas.uuid(d.cuentaId(), CuentaId::valor));
		parametros.put("documento", Columnas.uuid(d.documentoId(), DocumentoId::valor));
		parametros.put("fechaContable", d.fechaContable());
		parametros.put("fechaValor", d.fechaValor());
		parametros.put("concepto", d.concepto().valor());
		parametros.put("nota", d.nota());
		parametros.put("importe", Columnas.valor(d.importe()));
		parametros.put("saldo", Columnas.valor(d.saldo()));
		parametros.put("orden", d.orden());
		parametros.put("ordenDocumento", d.ordenDocumento());
		parametros.put("origen", d.origen().name());
		parametros.put("clave", d.claveDuplicado().valor());
		parametros.put("version", transaccion.version());
		return parametros;
	}

	@Override
	public Optional<Transaccion> buscar(TransaccionId id) {
		return this.jdbc.sql("SELECT " + COLUMNAS + " FROM transaccion WHERE id = :id").param("id", id.valor()).query(MAPEO).optional();
	}

	@Override
	public void eliminar(Transaccion transaccion) {
		this.jdbc.sql("DELETE FROM transaccion WHERE id = :id").param("id", transaccion.id().valor()).update();
	}

	@Override
	public void eliminarDelUsuario(UsuarioId usuario) {
		this.jdbc.sql("DELETE FROM transaccion WHERE usuario_id = :usuario").param("usuario", usuario.valor()).update();
	}

	@Override
	public boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids) {
		return !ids.isEmpty() && this.jdbc.sql("SELECT COUNT(*) FROM transaccion WHERE id IN (:ids) AND usuario_id <> :usuario")
				.param("ids", ids).param("usuario", usuario.valor()).query(Long.class).single() > 0;
	}

	@Override
	public long contarEnCuenta(CuentaId cuenta) {
		return this.jdbc.sql("SELECT COUNT(*) FROM transaccion WHERE cuenta_id = :cuenta")
				.param("cuenta", cuenta.valor()).query(Long.class).single();
	}

	@Override
	public List<Transaccion> sinDocumentoDeCuenta(CuentaId cuenta) {
		return this.jdbc.sql("SELECT " + COLUMNAS + " FROM transaccion WHERE cuenta_id = :cuenta AND documento_id IS NULL")
				.param("cuenta", cuenta.valor()).query(MAPEO).list();
	}

	@Override
	public int eliminarDeDocumento(DocumentoId documento) {
		return this.jdbc.sql("DELETE FROM transaccion WHERE documento_id = :documento")
				.param("documento", documento.valor()).update();
	}

	@Override
	public void insertarTodas(List<Transaccion> nuevas) {
		if (nuevas.isEmpty()) {
			return;
		}
		Map<String, Object>[] lote = nuevas.stream().map(TransaccionRepositorioJdbc::parametros).toArray(Map[]::new);
		this.plantilla.batchUpdate("INSERT INTO transaccion (" + COLUMNAS + ") VALUES (:id, :usuario, :cuenta, :documento,"
				+ " :fechaContable, :fechaValor, :concepto, :nota, :importe, :saldo, :orden, :ordenDocumento, :origen, :clave, :version)",
				SqlParameterSourceUtils.createBatch(lote));
	}

	@Override
	public Map<ClaveDuplicado, Long> contarClaves(UsuarioId usuario, CuentaId cuenta, Set<ClaveDuplicado> claves) {
		if (claves.isEmpty()) {
			return Map.of();
		}
		Map<ClaveDuplicado, Long> conteo = new HashMap<>();
		this.jdbc.sql("""
						SELECT clave_duplicado, COUNT(*) AS veces FROM transaccion
						WHERE usuario_id = :usuario AND (cuenta_id = :cuenta OR cuenta_id IS NULL) AND clave_duplicado IN (:claves)
						GROUP BY clave_duplicado
						""")
				.param("usuario", usuario.valor())
				.param("cuenta", cuenta.valor())
				.param("claves", claves.stream().map(ClaveDuplicado::valor).toList())
				.query(rs -> {
					conteo.put(new ClaveDuplicado(rs.getString("clave_duplicado")), rs.getLong("veces"));
				});
		return conteo;
	}

	@Override
	public List<Transaccion> buscarVarias(UsuarioId usuario, Collection<TransaccionId> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("SELECT " + COLUMNAS + " FROM transaccion WHERE usuario_id = :usuario AND id IN (:ids)")
				.param("usuario", usuario.valor())
				.param("ids", ids.stream().map(TransaccionId::valor).toList())
				.query(MAPEO)
				.list();
	}

	@Override
	public int moverDocumentoACuenta(DocumentoId documento, CuentaId cuenta) {
		return this.jdbc.sql("UPDATE transaccion SET cuenta_id = :cuenta, version = version + 1 WHERE documento_id = :documento")
				.param("cuenta", cuenta.valor())
				.param("documento", documento.valor())
				.update();
	}
}
