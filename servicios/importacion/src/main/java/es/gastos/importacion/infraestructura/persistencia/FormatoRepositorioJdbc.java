package es.gastos.importacion.infraestructura.persistencia;

import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.FormatoFecha;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.importacion.dominio.formato.FormatoRepositorio;
import es.gastos.importacion.dominio.formato.MapeoColumnas;
import es.gastos.importacion.dominio.formato.NombreFormato;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de formatos de banco con SQL explícito y bloqueo optimista por versión (docs/adr/0022). */
@Repository
class FormatoRepositorioJdbc implements FormatoRepositorio {

	private static final String COLUMNAS = """
			SELECT id, usuario_id, nombre, fila_inicio, columna_concepto, columna_importe, columna_fecha_contable,
			       columna_fecha_valor, columna_saldo, formato_fecha, version
			FROM formato_banco""";

	private final JdbcClient jdbc;

	FormatoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(FormatoBanco formato) {
		if (!formato.tieneCambios()) {
			return;
		}
		MapeoColumnas columnas = formato.columnas();
		Map<String, Object> parametros = new HashMap<>();
		parametros.put("id", formato.id().valor());
		parametros.put("usuario", formato.propietario().valor());
		parametros.put("nombre", formato.nombre().valor());
		parametros.put("normalizado", formato.nombre().normalizado());
		parametros.put("filaInicio", formato.filaInicio());
		parametros.put("concepto", columnas.concepto());
		parametros.put("importe", columnas.importe());
		parametros.put("fechaContable", columnas.fechaContable());
		parametros.put("fechaValor", columnas.fechaValor());
		parametros.put("saldo", columnas.saldo());
		parametros.put("formatoFecha", formato.formatoFecha().patron());
		parametros.put("version", formato.version());
		parametros.put("versionCargada", formato.versionCargada());

		try {
			if (formato.esNuevo()) {
				this.jdbc.sql("""
								INSERT INTO formato_banco (id, usuario_id, nombre, nombre_normalizado, fila_inicio, columna_concepto,
								       columna_importe, columna_fecha_contable, columna_fecha_valor, columna_saldo, formato_fecha, version)
								VALUES (:id, :usuario, :nombre, :normalizado, :filaInicio, :concepto, :importe, :fechaContable,
								        :fechaValor, :saldo, :formatoFecha, :version)""")
						.params(parametros).update();
				return;
			}
			int actualizadas = this.jdbc.sql("""
							UPDATE formato_banco SET nombre = :nombre, nombre_normalizado = :normalizado, fila_inicio = :filaInicio,
							       columna_concepto = :concepto, columna_importe = :importe, columna_fecha_contable = :fechaContable,
							       columna_fecha_valor = :fechaValor, columna_saldo = :saldo, formato_fecha = :formatoFecha,
							       version = :version
							WHERE id = :id AND version = :versionCargada""")
					.params(parametros).update();
			if (actualizadas == 0) {
				throw Conflicto.modificacionConcurrente("el formato", formato.id());
			}
		} catch (DuplicateKeyException e) {
			// Dos altas simultáneas con el mismo nombre: la comprobación previa no las ve, el índice único sí
			throw Conflicto.nombreDuplicado("un formato", formato.nombre().valor());
		}
	}

	@Override
	public Optional<FormatoBanco> buscar(FormatoId id) {
		return this.jdbc.sql(COLUMNAS + " WHERE id = :id").param("id", id.valor()).query(FormatoRepositorioJdbc::formato).optional();
	}

	@Override
	public List<FormatoBanco> delUsuario(UsuarioId usuario) {
		return this.jdbc.sql(COLUMNAS + " WHERE usuario_id = :usuario ORDER BY nombre_normalizado")
				.param("usuario", usuario.valor()).query(FormatoRepositorioJdbc::formato).list();
	}

	@Override
	public boolean existeNombre(UsuarioId usuario, String nombreNormalizado, FormatoId excepto) {
		// Se usa un id imposible en vez de un parámetro nulo para no depender de cómo enlaza el driver los NULL
		UUID excluido = excepto == null ? new UUID(0, 0) : excepto.valor();
		return this.jdbc.sql("""
						SELECT COUNT(*) FROM formato_banco
						WHERE usuario_id = :usuario AND nombre_normalizado = :normalizado AND id <> :excepto""")
				.param("usuario", usuario.valor())
				.param("normalizado", nombreNormalizado)
				.param("excepto", excluido)
				.query(Long.class).single() > 0;
	}

	@Override
	public void eliminar(FormatoBanco formato) {
		this.jdbc.sql("DELETE FROM formato_banco WHERE id = :id").param("id", formato.id().valor()).update();
	}

	@Override
	public void eliminarDelUsuario(UsuarioId usuario) {
		this.jdbc.sql("DELETE FROM formato_banco WHERE usuario_id = :usuario").param("usuario", usuario.valor()).update();
	}

	@Override
	public boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<FormatoId> ids) {
		return !ids.isEmpty() && this.jdbc.sql("SELECT COUNT(*) FROM formato_banco WHERE id IN (:ids) AND usuario_id <> :usuario")
				.param("ids", ids.stream().map(FormatoId::valor).toList()).param("usuario", usuario.valor())
				.query(Long.class).single() > 0;
	}

	private static FormatoBanco formato(ResultSet rs, int fila) throws SQLException {
		return FormatoBanco.reconstituir(new FormatoId(rs.getObject("id", UUID.class)), new UsuarioId(rs.getObject("usuario_id", UUID.class)),
				new NombreFormato(rs.getString("nombre")), rs.getInt("fila_inicio"),
				new MapeoColumnas(rs.getInt("columna_concepto"), rs.getInt("columna_importe"), rs.getInt("columna_fecha_contable"),
						rs.getObject("columna_fecha_valor", Integer.class), rs.getObject("columna_saldo", Integer.class)),
				new FormatoFecha(rs.getString("formato_fecha")), rs.getLong("version"));
	}
}
