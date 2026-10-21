package es.gastos.identidad.dominio.usuario;

import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.UsuarioId;

import java.time.Instant;
import java.util.Objects;

/**
 * Raíz del agregado usuario. Se crea solo mediante {@link #registrar}, que emite
 * {@link UsuarioRegistrado}; desde la base de datos se reconstruye con {@link #reconstituir}.
 */
public final class Usuario extends RaizAgregado<UsuarioId> {

	private final UsuarioId id;

	private final Email email;

	private final Nombre nombre;

	private final HashContrasena hashContrasena;

	private final Instant registradoEn;

	private Usuario(UsuarioId id, Email email, Nombre nombre, HashContrasena hashContrasena, Instant registradoEn,
			long version) {
		super(version);
		this.id = Objects.requireNonNull(id);
		this.email = Objects.requireNonNull(email);
		this.nombre = Objects.requireNonNull(nombre);
		this.hashContrasena = Objects.requireNonNull(hashContrasena);
		this.registradoEn = Objects.requireNonNull(registradoEn);
	}

	/**
	 * Registra un usuario nuevo. La unicidad del email no se puede comprobar dentro del agregado
	 * (abarca a todos los usuarios): la garantiza el caso de uso y, ante carreras, la base de datos.
	 */
	public static Usuario registrar(UsuarioId id, Email email, Nombre nombre, HashContrasena hash, Instant ahora) {
		Usuario usuario = new Usuario(id, email, nombre, hash, ahora, 0);
		usuario.registrarEvento(new UsuarioRegistrado(id, usuario.siguienteVersion(), email, nombre));
		return usuario;
	}

	public static Usuario reconstituir(UsuarioId id, Email email, Nombre nombre, HashContrasena hash, Instant registradoEn,
			long version) {
		return new Usuario(id, email, nombre, hash, registradoEn, version);
	}

	@Override
	public UsuarioId id() {
		return this.id;
	}

	@Override
	public UsuarioId propietario() {
		return this.id;
	}

	public Email email() {
		return this.email;
	}

	public Nombre nombre() {
		return this.nombre;
	}

	public HashContrasena hashContrasena() {
		return this.hashContrasena;
	}

	public Instant registradoEn() {
		return this.registradoEn;
	}
}
