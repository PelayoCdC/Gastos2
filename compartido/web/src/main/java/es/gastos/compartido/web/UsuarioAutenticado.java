package es.gastos.compartido.web;

import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * El usuario y el JWT de la petición en curso, para los adaptadores que no reciben
 * {@code @UsuarioActual}: interceptores y clientes HTTP que llaman a otro servicio reenviando el
 * token del usuario (sin cuentas de servicio, docs/adr/0008).
 */
public final class UsuarioAutenticado {

	private UsuarioAutenticado() {
	}

	public static UsuarioId usuario() {
		return UsuarioId.de(jwt().getSubject());
	}

	/** Valor de la cabecera {@code Authorization} para reenviar el token a otro servicio. */
	public static String cabeceraAutorizacion() {
		return "Bearer " + jwt().getTokenValue();
	}

	private static Jwt jwt() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof Jwt jwt) {
			return jwt;
		}
		throw new AuthenticationCredentialsNotFoundException("La peticion no lleva un JWT valido");
	}
}
