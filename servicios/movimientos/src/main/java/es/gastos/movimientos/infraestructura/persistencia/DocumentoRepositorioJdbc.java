package es.gastos.movimientos.infraestructura.persistencia;

import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.Documento;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import es.gastos.movimientos.dominio.documento.DocumentoRepositorio;
import es.gastos.movimientos.dominio.documento.NombreDocumento;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class DocumentoRepositorioJdbc implements DocumentoRepositorio {

	private static final String COLUMNAS = "id, usuario_id, cuenta_id, nombre, nombre_archivo, fecha, resultado_registro, version";

	private static final RowMapper<Documento> MAPEO = (rs, n) -> Documento.reconstituir(
			new DocumentoId(rs.getObject("id", UUID.class)),
			new UsuarioId(rs.getObject("usuario_id", UUID.class)),
			Columnas.id(rs, "cuenta_id", CuentaId::new),
			new NombreDocumento(rs.getString("nombre")),
			rs.getString("nombre_archivo"),
			Columnas.fecha(rs, "fecha"),
			rs.getString("resultado_registro"),
			rs.getLong("version"));

	private final JdbcClient jdbc;

	DocumentoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(Documento documento) {
		if (!documento.tieneCambios()) {
			return;
		}

		if (documento.esNuevo()) {
			this.jdbc.sql("INSERT INTO documento (" + COLUMNAS + ") VALUES (:id, :usuario, :cuenta, :nombre, :archivo, :fecha, :resultado, :version)")
					.param("id", documento.id().valor())
					.param("usuario", documento.propietario().valor())
					.param("cuenta", Columnas.uuid(documento.cuentaId(), CuentaId::valor))
					.param("nombre", documento.nombre().valor())
					.param("archivo", documento.nombreArchivo())
					.param("fecha", documento.fecha())
					.param("resultado", documento.resultadoRegistro())
					.param("version", documento.version())
					.update();
			return;
		}

		int actualizadas = this.jdbc.sql("""
						UPDATE documento SET cuenta_id = :cuenta, nombre = :nombre, resultado_registro = :resultado, version = :version
						WHERE id = :id AND version = :versionCargada
						""")
				.param("cuenta", Columnas.uuid(documento.cuentaId(), CuentaId::valor))
				.param("nombre", documento.nombre().valor())
				.param("resultado", documento.resultadoRegistro())
				.param("version", documento.version())
				.param("id", documento.id().valor())
				.param("versionCargada", documento.versionCargada())
				.update();
		if (actualizadas == 0) {
			throw Conflicto.modificacionConcurrente("el documento", documento.id());
		}
	}

	@Override
	public Optional<Documento> buscar(DocumentoId id) {
		return this.jdbc.sql("SELECT " + COLUMNAS + " FROM documento WHERE id = :id").param("id", id.valor()).query(MAPEO).optional();
	}

	@Override
	public List<Documento> deCuenta(CuentaId cuenta) {
		return this.jdbc.sql("SELECT " + COLUMNAS + " FROM documento WHERE cuenta_id = :cuenta ORDER BY fecha, id")
				.param("cuenta", cuenta.valor())
				.query(MAPEO)
				.list();
	}

	@Override
	public void eliminar(Documento documento) {
		this.jdbc.sql("DELETE FROM documento WHERE id = :id").param("id", documento.id().valor()).update();
	}

	@Override
	public void eliminarDelUsuario(UsuarioId usuario) {
		this.jdbc.sql("DELETE FROM documento WHERE usuario_id = :usuario").param("usuario", usuario.valor()).update();
	}

	@Override
	public boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids) {
		return !ids.isEmpty() && this.jdbc.sql("SELECT COUNT(*) FROM documento WHERE id IN (:ids) AND usuario_id <> :usuario")
				.param("ids", ids).param("usuario", usuario.valor()).query(Long.class).single() > 0;
	}
}
