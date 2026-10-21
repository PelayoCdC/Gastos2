package es.gastos.identidad.infraestructura.persistencia;

import es.gastos.identidad.aplicacion.RegistrarUsuario;
import es.gastos.identidad.dominio.usuario.Email;
import es.gastos.identidad.dominio.usuario.HashContrasena;
import es.gastos.identidad.dominio.usuario.Nombre;
import es.gastos.identidad.dominio.usuario.Usuario;
import es.gastos.identidad.dominio.usuario.UsuarioRepositorio;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** Adaptador del repositorio de usuarios con SQL explícito (docs/adr/0022). */
@Repository
class UsuarioRepositorioJdbc implements UsuarioRepositorio {

	private static final String COLUMNAS = "id, email, nombre, hash_contrasena, registrado_en, version";

	private static final RowMapper<Usuario> MAPEO = (rs, n) -> Usuario.reconstituir(
			new UsuarioId(rs.getObject("id", UUID.class)),
			new Email(rs.getString("email")),
			new Nombre(rs.getString("nombre")),
			new HashContrasena(rs.getString("hash_contrasena")),
			rs.getObject("registrado_en", LocalDateTime.class).toInstant(ZoneOffset.UTC),
			rs.getLong("version"));

	private final JdbcClient jdbc;

	UsuarioRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(Usuario usuario) {
		if (!usuario.tieneCambios()) {
			return;
		}

		if (usuario.esNuevo()) {
			this.insertar(usuario);
			return;
		}

		int actualizadas = this.jdbc.sql("""
						UPDATE usuario SET email = :email, nombre = :nombre, hash_contrasena = :hash, version = :version
						WHERE id = :id AND version = :versionCargada
						""")
				.param("email", usuario.email().valor())
				.param("nombre", usuario.nombre().valor())
				.param("hash", usuario.hashContrasena().valor())
				.param("version", usuario.version())
				.param("id", usuario.id().valor())
				.param("versionCargada", usuario.versionCargada())
				.update();

		if (actualizadas == 0) {
			throw Conflicto.modificacionConcurrente("el usuario", usuario.id());
		}
	}

	private void insertar(Usuario usuario) {
		try {
			this.jdbc.sql("INSERT INTO usuario (" + COLUMNAS + ") VALUES (:id, :email, :nombre, :hash, :registradoEn, :version)")
					.param("id", usuario.id().valor())
					.param("email", usuario.email().valor())
					.param("nombre", usuario.nombre().valor())
					.param("hash", usuario.hashContrasena().valor())
					.param("registradoEn", LocalDateTime.ofInstant(usuario.registradoEn(), ZoneOffset.UTC))
					.param("version", usuario.version())
					.update();
		} catch (DuplicateKeyException e) {
			// Dos registros simultáneos con el mismo email: la restricción única de la base de datos decide
			throw RegistrarUsuario.emailYaRegistrado();
		}
	}

	@Override
	public Optional<Usuario> buscarPorId(UsuarioId id) {
		return this.jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE id = :id")
				.param("id", id.valor())
				.query(MAPEO)
				.optional();
	}

	@Override
	public Optional<Usuario> buscarPorEmail(Email email) {
		return this.jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE email = :email")
				.param("email", email.valor())
				.query(MAPEO)
				.optional();
	}

	@Override
	public boolean existeEmail(Email email) {
		return this.jdbc.sql("SELECT COUNT(*) FROM usuario WHERE email = :email")
				.param("email", email.valor())
				.query(Long.class)
				.single() > 0;
	}
}
