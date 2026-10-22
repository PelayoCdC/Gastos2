package es.gastos.compartido.mensajeria;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

/**
 * Añade {@code Gastos-Secuencia: <contexto>=<n>} a las respuestas correctas de los comandos
 * (docs/adr/0011). La secuencia la anota {@link PublicadorEventosOutbox} al escribir en el outbox,
 * dentro de la transacción del caso de uso.
 *
 * <p>La cabecera tiene que salir antes que el cuerpo, pero la secuencia solo se conoce cuando el caso
 * de uso ha terminado. Por eso la respuesta se envuelve y la cabecera se pone en el último momento
 * posible: al empezar a escribir el cuerpo o, si no hay cuerpo (204), al terminar la petición. Las
 * respuestas de error no la llevan: su transacción se deshizo y esos eventos no existen.
 */
class FiltroSecuenciaPublicada extends OncePerRequestFilter {

	private static final Set<String> LECTURAS = Set.of("GET", "HEAD", "OPTIONS");

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		// Restos de un uso anterior del hilo (por ejemplo, un consumidor de Kafka) no son de esta petición
		SecuenciaPublicada.consumir();
		if (LECTURAS.contains(peticion.getMethod())) {
			try {
				cadena.doFilter(peticion, respuesta);
			} finally {
				SecuenciaPublicada.consumir();
			}
			return;
		}

		RespuestaConSecuencia envoltorio = new RespuestaConSecuencia(respuesta);
		try {
			cadena.doFilter(peticion, envoltorio);
		} finally {
			envoltorio.anotar();
		}
	}

	private static final class RespuestaConSecuencia extends HttpServletResponseWrapper {

		private boolean anotada;

		RespuestaConSecuencia(HttpServletResponse respuesta) {
			super(respuesta);
		}

		void anotar() {
			if (this.anotada) {
				return;
			}
			this.anotada = true;
			String secuencias = SecuenciaPublicada.consumir();
			if (!secuencias.isEmpty() && !isCommitted() && getStatus() < 400) {
				setHeader(SecuenciaPublicada.CABECERA, secuencias);
			}
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			this.anotar();
			return super.getOutputStream();
		}

		@Override
		public PrintWriter getWriter() throws IOException {
			this.anotar();
			return super.getWriter();
		}

		@Override
		public void flushBuffer() throws IOException {
			this.anotar();
			super.flushBuffer();
		}

		/** Un error nunca lleva secuencia: se olvida la que hubiera. */
		private void descartar() {
			this.anotada = true;
			SecuenciaPublicada.consumir();
		}

		@Override
		public void sendError(int estado, String mensaje) throws IOException {
			this.descartar();
			super.sendError(estado, mensaje);
		}

		@Override
		public void sendError(int estado) throws IOException {
			this.descartar();
			super.sendError(estado);
		}

		@Override
		public void sendRedirect(String destino) throws IOException {
			this.anotar();
			super.sendRedirect(destino);
		}
	}
}
