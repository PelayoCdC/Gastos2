package es.gastos.identidad.aplicacion;

import es.gastos.identidad.dominio.usuario.ContrasenaEnClaro;
import es.gastos.identidad.dominio.usuario.Email;
import es.gastos.identidad.dominio.usuario.HashContrasena;
import es.gastos.identidad.dominio.usuario.Nombre;
import es.gastos.identidad.dominio.usuario.Usuario;
import es.gastos.identidad.dominio.usuario.UsuarioRepositorio;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;

import java.time.Clock;

/** Caso de uso: registrar un usuario nuevo y anunciarlo con {@code UsuarioRegistrado}. */
@CasoDeUso
public class RegistrarUsuario {

	public record Comando(String nombre, String email, String contrasena) {
	}

	private final UsuarioRepositorio usuarios;

	private final CifradorContrasenas cifrador;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	private final Clock reloj;

	public RegistrarUsuario(UsuarioRepositorio usuarios, CifradorContrasenas cifrador, PublicadorEventos publicador,
			UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids, Clock reloj) {
		this.usuarios = usuarios;
		this.cifrador = cifrador;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
		this.reloj = reloj;
	}

	public PerfilUsuario ejecutar(Comando comando) {
		Nombre nombre = new Nombre(comando.nombre());
		Email email = new Email(comando.email());
		ContrasenaEnClaro contrasena = new ContrasenaEnClaro(comando.contrasena());

		// BCrypt es lento a propósito: se calcula antes de abrir la transacción para no alargarla
		HashContrasena hash = this.cifrador.cifrar(contrasena);

		return this.unidadDeTrabajo.ejecutar(() -> {
			if (this.usuarios.existeEmail(email)) {
				throw emailYaRegistrado();
			}

			Usuario usuario = Usuario.registrar(new UsuarioId(this.ids.nuevo()), email, nombre, hash, this.reloj.instant());
			this.usuarios.guardar(usuario);
			this.publicador.publicarDe(usuario);
			return PerfilUsuario.de(usuario);
		});
	}

	public static Conflicto emailYaRegistrado() {
		return new Conflicto("EMAIL_YA_REGISTRADO", "Ya existe un usuario con ese email");
	}
}
