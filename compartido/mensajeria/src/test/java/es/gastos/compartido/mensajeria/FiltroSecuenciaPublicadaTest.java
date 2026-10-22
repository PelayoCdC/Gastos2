package es.gastos.compartido.mensajeria;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroSecuenciaPublicadaTest {

	private final FiltroSecuenciaPublicada filtro = new FiltroSecuenciaPublicada();

	@AfterEach
	void limpiar() {
		SecuenciaPublicada.consumir();
	}

	@Test
	void unComandoCorrectoDevuelveLaMayorSecuenciaDeCadaContexto() throws Exception {
		MockHttpServletResponse respuesta = this.ejecutar("POST", (peticion, r) -> {
			SecuenciaPublicada.registrar("movimientos", 7);
			SecuenciaPublicada.registrar("movimientos", 5);
			SecuenciaPublicada.registrarCabecera("clasificacion=3, basura, otro=x");
			r.getWriter().write("{}");
		});

		assertThat(respuesta.getHeader(SecuenciaPublicada.CABECERA)).isEqualTo("movimientos=7,clasificacion=3");
	}

	@Test
	void sinCuerpoLaCabeceraSePoneAlTerminar() throws Exception {
		MockHttpServletResponse respuesta = this.ejecutar("DELETE", (peticion, r) -> {
			SecuenciaPublicada.registrar("movimientos", 9);
			((jakarta.servlet.http.HttpServletResponse) r).setStatus(204);
		});

		assertThat(respuesta.getHeader(SecuenciaPublicada.CABECERA)).isEqualTo("movimientos=9");
	}

	@Test
	void losErroresYLasLecturasNoLlevanSecuencia() throws Exception {
		MockHttpServletResponse conflicto = this.ejecutar("PUT", (peticion, r) -> {
			SecuenciaPublicada.registrar("movimientos", 11);
			((jakarta.servlet.http.HttpServletResponse) r).setStatus(409);
			r.getWriter().write("{}");
		});
		MockHttpServletResponse error = this.ejecutar("POST", (peticion, r) -> {
			SecuenciaPublicada.registrar("movimientos", 12);
			((jakarta.servlet.http.HttpServletResponse) r).sendError(500);
		});
		MockHttpServletResponse lectura = this.ejecutar("GET", (peticion, r) -> SecuenciaPublicada.registrar("movimientos", 13));

		assertThat(conflicto.getHeader(SecuenciaPublicada.CABECERA)).isNull();
		assertThat(error.getHeader(SecuenciaPublicada.CABECERA)).isNull();
		assertThat(lectura.getHeader(SecuenciaPublicada.CABECERA)).isNull();
		assertThat(SecuenciaPublicada.consumir()).as("nada queda pendiente para la siguiente petición del hilo").isEmpty();
	}

	@Test
	void losRestosDeOtroUsoDelHiloNoSeCuelanEnLaRespuesta() throws Exception {
		SecuenciaPublicada.registrar("clasificacion", 99);

		MockHttpServletResponse respuesta = this.ejecutar("POST", (peticion, r) -> r.getWriter().write("{}"));

		assertThat(respuesta.getHeader(SecuenciaPublicada.CABECERA)).isNull();
	}

	private MockHttpServletResponse ejecutar(String metodo, FilterChain cadena) throws Exception {
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		this.filtro.doFilter(new MockHttpServletRequest(metodo, "/x"), respuesta, cadena);
		return respuesta;
	}
}
