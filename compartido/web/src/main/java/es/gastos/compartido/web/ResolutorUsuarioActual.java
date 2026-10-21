package es.gastos.compartido.web;

import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resuelve los parámetros {@code @UsuarioActual UsuarioId} desde el JWT autenticado. */
class ResolutorUsuarioActual implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parametro) {
		return parametro.hasParameterAnnotation(UsuarioActual.class)
				&& UsuarioId.class.equals(parametro.getParameterType());
	}

	@Override
	public UsuarioId resolveArgument(MethodParameter parametro, ModelAndViewContainer modelo,
			NativeWebRequest peticion, WebDataBinderFactory binders) {
		return usuarioDelContexto();
	}

	static UsuarioId usuarioDelContexto() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof Jwt jwt) {
			return UsuarioId.de(jwt.getSubject());
		}
		throw new AuthenticationCredentialsNotFoundException("La peticion no lleva un JWT valido");
	}
}
