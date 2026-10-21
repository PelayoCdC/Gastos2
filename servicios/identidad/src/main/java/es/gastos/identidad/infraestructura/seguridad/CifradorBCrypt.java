package es.gastos.identidad.infraestructura.seguridad;

import es.gastos.identidad.aplicacion.CifradorContrasenas;
import es.gastos.identidad.dominio.usuario.ContrasenaEnClaro;
import es.gastos.identidad.dominio.usuario.HashContrasena;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Adaptador de {@link CifradorContrasenas} con BCrypt (coste 10, el valor por defecto de Spring Security). */
@Component
class CifradorBCrypt implements CifradorContrasenas {

	private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

	// Hash de una contraseña cualquiera, para gastar el mismo tiempo cuando el email no existe
	private final String hashFicticio = this.bcrypt.encode("contrasena-ficticia-para-igualar-tiempos");

	@Override
	public HashContrasena cifrar(ContrasenaEnClaro contrasena) {
		return new HashContrasena(this.bcrypt.encode(contrasena.valor()));
	}

	@Override
	public boolean coincide(ContrasenaEnClaro contrasena, HashContrasena hash) {
		return this.bcrypt.matches(contrasena.valor(), hash.valor());
	}

	@Override
	public void simularComprobacion(ContrasenaEnClaro contrasena) {
		this.bcrypt.matches(contrasena.valor(), this.hashFicticio);
	}
}
