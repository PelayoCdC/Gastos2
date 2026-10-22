package es.gastos.movimientos.infraestructura.persistencia;

import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.Cuenta;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.cuenta.CuentaRepositorio;
import es.gastos.movimientos.dominio.cuenta.NombreCuenta;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
class CuentaRepositorioJdbc implements CuentaRepositorio {

	private static final String SELECT = "SELECT id, usuario_id, nombre, banco, descripcion, version FROM cuenta WHERE id = :id";

	private static final RowMapper<Cuenta> MAPEO = (rs, n) -> Cuenta.reconstituir(
			new CuentaId(rs.getObject("id", UUID.class)),
			new UsuarioId(rs.getObject("usuario_id", UUID.class)),
			new NombreCuenta(rs.getString("nombre")),
			rs.getString("banco"),
			rs.getString("descripcion"),
			rs.getLong("version"));

	private final JdbcClient jdbc;

	CuentaRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(Cuenta cuenta) {
		if (!cuenta.tieneCambios()) {
			return;
		}

		try {
			if (cuenta.esNuevo()) {
				this.jdbc.sql("""
								INSERT INTO cuenta (id, usuario_id, nombre, nombre_normalizado, banco, descripcion, version)
								VALUES (:id, :usuario, :nombre, :normalizado, :banco, :descripcion, :version)
								""")
						.params(this.parametros(cuenta))
						.param("usuario", cuenta.propietario().valor())
						.update();
				return;
			}

			int actualizadas = this.jdbc.sql("""
							UPDATE cuenta SET nombre = :nombre, nombre_normalizado = :normalizado, banco = :banco,
							                  descripcion = :descripcion, version = :version
							WHERE id = :id AND version = :versionCargada
							""")
					.params(this.parametros(cuenta))
					.param("versionCargada", cuenta.versionCargada())
					.update();
			if (actualizadas == 0) {
				throw Conflicto.modificacionConcurrente("la cuenta", cuenta.id());
			}
		} catch (DuplicateKeyException e) {
			throw Conflicto.nombreDuplicado("una cuenta", cuenta.nombre().valor());
		}
	}

	private java.util.Map<String, Object> parametros(Cuenta cuenta) {
		java.util.Map<String, Object> parametros = new java.util.HashMap<>();
		parametros.put("id", cuenta.id().valor());
		parametros.put("nombre", cuenta.nombre().valor());
		parametros.put("normalizado", cuenta.nombre().normalizado());
		parametros.put("banco", cuenta.banco());
		parametros.put("descripcion", cuenta.descripcion());
		parametros.put("version", cuenta.version());
		return parametros;
	}

	@Override
	public Optional<Cuenta> buscar(CuentaId id) {
		return this.jdbc.sql(SELECT).param("id", id.valor()).query(MAPEO).optional();
	}

	@Override
	public Optional<Cuenta> buscarParaActualizar(CuentaId id) {
		return this.jdbc.sql(SELECT + " FOR UPDATE").param("id", id.valor()).query(MAPEO).optional();
	}

	@Override
	public boolean existeNombre(UsuarioId usuario, String nombreNormalizado, CuentaId excepto) {
		// Se usa un id imposible en vez de un parámetro nulo para no depender de cómo enlaza el driver los NULL
		UUID excluido = excepto == null ? new UUID(0, 0) : excepto.valor();
		return this.jdbc.sql("""
						SELECT COUNT(*) FROM cuenta
						WHERE usuario_id = :usuario AND nombre_normalizado = :nombre AND id <> :excepto
						""")
				.param("usuario", usuario.valor())
				.param("nombre", nombreNormalizado)
				.param("excepto", excluido)
				.query(Long.class)
				.single() > 0;
	}

	@Override
	public void eliminar(Cuenta cuenta) {
		this.jdbc.sql("DELETE FROM cuenta WHERE id = :id").param("id", cuenta.id().valor()).update();
	}

	@Override
	public void eliminarDelUsuario(UsuarioId usuario) {
		this.jdbc.sql("DELETE FROM cuenta WHERE usuario_id = :usuario").param("usuario", usuario.valor()).update();
	}

	@Override
	public boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids) {
		return !ids.isEmpty() && this.jdbc.sql("SELECT COUNT(*) FROM cuenta WHERE id IN (:ids) AND usuario_id <> :usuario")
				.param("ids", ids).param("usuario", usuario.valor()).query(Long.class).single() > 0;
	}
}
