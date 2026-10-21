package es.gastos.identidad.aplicacion;

import es.gastos.identidad.dominio.usuario.ContrasenaEnClaro;
import es.gastos.identidad.dominio.usuario.Email;
import es.gastos.identidad.dominio.usuario.HashContrasena;
import es.gastos.identidad.dominio.usuario.Usuario;
import es.gastos.identidad.dominio.usuario.UsuarioRepositorio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Dobles de los puertos de identidad para los tests de aplicación. */
final class Dobles {

	private Dobles() {
	}

	static final class UsuariosEnMemoria implements UsuarioRepositorio {

		final Map<UsuarioId, Usuario> usuarios = new HashMap<>();

		@Override
		public void guardar(Usuario usuario) {
			this.usuarios.put(usuario.id(), usuario);
		}

		@Override
		public Optional<Usuario> buscarPorId(UsuarioId id) {
			return Optional.ofNullable(this.usuarios.get(id));
		}

		@Override
		public Optional<Usuario> buscarPorEmail(Email email) {
			return this.usuarios.values().stream().filter(u -> u.email().equals(email)).findFirst();
		}

		@Override
		public boolean existeEmail(Email email) {
			return this.buscarPorEmail(email).isPresent();
		}
	}

	/** "Cifra" anteponiendo un prefijo: suficiente para comprobar que nunca se guarda el texto en claro. */
	static final class CifradorFalso implements CifradorContrasenas {

		int simulaciones;

		@Override
		public HashContrasena cifrar(ContrasenaEnClaro contrasena) {
			return new HashContrasena("hash:" + contrasena.valor());
		}

		@Override
		public boolean coincide(ContrasenaEnClaro contrasena, HashContrasena hash) {
			return hash.valor().equals("hash:" + contrasena.valor());
		}

		@Override
		public void simularComprobacion(ContrasenaEnClaro contrasena) {
			this.simulaciones++;
		}
	}

	static final class EmisorFalso implements EmisorTokens {

		@Override
		public TokenEmitido emitir(Usuario usuario) {
			return new TokenEmitido("token-de-" + usuario.email().valor(), 3600);
		}
	}
}
