package es.gastos.gateway;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * Si el servicio de destino no responde (caído, sin desplegar o arrancando), el cliente recibe un
 * 503 con el mismo formato RFC 9457 que el resto de errores, en vez de un 500 genérico: así sabe que
 * puede reintentar.
 */
@Component
@Order(-2)
class ErroresGateway implements WebExceptionHandler {

	private static final String PROBLEMA = """
			{"type":"urn:gastos:problema:servicio-no-disponible","title":"Service Unavailable","status":503,\
			"detail":"El servicio de destino no esta disponible; reintenta en unos segundos","codigo":"SERVICIO_NO_DISPONIBLE"}""";

	@Override
	public Mono<Void> handle(ServerWebExchange intercambio, Throwable error) {
		if (!esServicioInalcanzable(error) || intercambio.getResponse().isCommitted()) {
			return Mono.error(error);
		}

		intercambio.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		intercambio.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		DataBuffer cuerpo = intercambio.getResponse().bufferFactory().wrap(PROBLEMA.getBytes(StandardCharsets.UTF_8));
		return intercambio.getResponse().writeWith(Mono.just(cuerpo));
	}

	private static boolean esServicioInalcanzable(Throwable error) {
		for (Throwable causa = error; causa != null; causa = causa.getCause()) {
			if (causa instanceof ConnectException || causa instanceof UnknownHostException
					|| causa.getClass().getSimpleName().equals("ConnectTimeoutException")) {
				return true;
			}
		}
		return false;
	}
}
