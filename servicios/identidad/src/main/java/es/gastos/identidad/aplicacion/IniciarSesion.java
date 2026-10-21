package es.gastos.identidad.aplicacion;

import es.gastos.identidad.dominio.usuario.ContrasenaEnClaro;
import es.gastos.identidad.dominio.usuario.Email;
import es.gastos.identidad.dominio.usuario.Usuario;
import es.gastos.identidad.dominio.usuario.UsuarioRepositorio;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ExcepcionDominio;

import java.util.Optional;

/**
 * Caso de uso: comprobar las credenciales y emitir un token. Cualquier fallo (email mal escrito,
 * inexistente o contraseña errónea) produce el mismo {@link CredencialesInvalidas}.
 */
@CasoDeUso
public class IniciarSesion {

	public record Comando(String email, String contrasena) {
	}

	public record Sesion(String token, String tipo, long expiraEnSegundos) {
	}

	private final UsuarioRepositorio usuarios;

	private final CifradorContrasenas cifrador;

	private final EmisorTokens emisor;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public IniciarSesion(UsuarioRepositorio usuarios, CifradorContrasenas cifrador, EmisorTokens emisor,
			UnidadDeTrabajo unidadDeTrabajo) {
		this.usuarios = usuarios;
		this.cifrador = cifrador;
		this.emisor = emisor;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public Sesion ejecutar(Comando comando) {
		ContrasenaEnClaro contrasena;
		Email email;
		try {
			contrasena = new ContrasenaEnClaro(comando.contrasena());
			email = new Email(comando.email());
		} catch (ExcepcionDominio e) {
			throw new CredencialesInvalidas();
		}

		Optional<Usuario> usuario = this.unidadDeTrabajo.ejecutarLectura(() -> this.usuarios.buscarPorEmail(email));

		if (usuario.isEmpty()) {
			this.cifrador.simularComprobacion(contrasena);
			throw new CredencialesInvalidas();
		}

		if (!this.cifrador.coincide(contrasena, usuario.get().hashContrasena())) {
			throw new CredencialesInvalidas();
		}

		EmisorTokens.TokenEmitido token = this.emisor.emitir(usuario.get());
		return new Sesion(token.token(), "Bearer", token.expiraEnSegundos());
	}
}
