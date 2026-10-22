package es.gastos.consultas.infraestructura.rest;

import es.gastos.compartido.web.UsuarioAutenticado;
import es.gastos.consultas.aplicacion.consistencia.EsperarConsistencia;
import es.gastos.consultas.dominio.consistencia.SecuenciasEsperadas;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Atiende {@code Gastos-Esperar} antes de ejecutar cualquier consulta (docs/adr/0011): espera a que
 * las proyecciones lleguen a las secuencias pedidas y, si no llegan a tiempo, responde igualmente con
 * {@code Gastos-Desfase: true}. Va antes del controlador para que la cabecera salga aunque el cuerpo
 * sea un Excel que se escribe de una vez.
 */
@Component
class InterceptorEsperaConsistencia implements HandlerInterceptor {

	static final String ESPERAR = "Gastos-Esperar";

	static final String DESFASE = "Gastos-Desfase";

	private final EsperarConsistencia esperar;

	InterceptorEsperaConsistencia(EsperarConsistencia esperar) {
		this.esperar = esperar;
	}

	@Override
	public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
		SecuenciasEsperadas esperadas = SecuenciasEsperadas.de(peticion.getHeader(ESPERAR));
		if (!esperadas.vacia() && !this.esperar.esperar(UsuarioAutenticado.usuario(), esperadas)) {
			respuesta.setHeader(DESFASE, "true");
		}
		return true;
	}
}
