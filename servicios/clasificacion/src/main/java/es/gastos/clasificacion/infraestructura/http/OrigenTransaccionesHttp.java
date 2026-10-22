package es.gastos.clasificacion.infraestructura.http;

import es.gastos.clasificacion.aplicacion.clasificacion.OrigenTransacciones;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.compartido.web.UsuarioAutenticado;
import es.gastos.kernel.aplicacion.ServicioNoDisponible;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador del read-through contra la API interna de movimientos. Reenvía el JWT del usuario de la
 * petición en curso, así movimientos aplica la misma comprobación de propiedad que en su API pública
 * y no hacen falta cuentas de servicio (docs/adr/0008).
 *
 * <p>Los fallos de red se reintentan dos veces ({@code @Retryable} de Spring Framework 7; las
 * consultas son idempotentes) y, si persisten, se convierten en {@link ServicioNoDisponible} (503).
 */
@Component
class OrigenTransaccionesHttp implements OrigenTransacciones {

	record RespuestaTransaccion(UUID id, UUID documentoId, String concepto) {
	}

	private final RestClient cliente;

	OrigenTransaccionesHttp(RestClient.Builder constructor, @Value("${gastos.servicios.movimientos}") String movimientos) {
		this.cliente = constructor.baseUrl(movimientos).build();
	}

	@Override
	@Retryable(includes = ResourceAccessException.class, maxRetries = 2, delay = 200)
	public Optional<HechosTransaccion> consultar(UsuarioId usuario, TransaccionId id) {
		try {
			RespuestaTransaccion respuesta = this.cliente.get()
					.uri("/internal/transacciones/{id}", id.valor())
					.header(HttpHeaders.AUTHORIZATION, UsuarioAutenticado.cabeceraAutorizacion())
					.retrieve()
					.body(RespuestaTransaccion.class);
			return Optional.ofNullable(respuesta).map(OrigenTransaccionesHttp::hechos);
		} catch (HttpClientErrorException.NotFound noExiste) {
			return Optional.empty();
		} catch (ResourceAccessException sinRed) {
			// Se deja pasar tal cual para que @Retryable reintente; si se agotan, la capa web da 503
			throw sinRed;
		} catch (RuntimeException e) {
			throw new ServicioNoDisponible("movimientos", e);
		}
	}

	@Override
	@Retryable(includes = ResourceAccessException.class, maxRetries = 2, delay = 200)
	public List<HechosTransaccion> consultarVarias(UsuarioId usuario, Collection<TransaccionId> ids) {
		RespuestaTransaccion[] respuesta = this.cliente.post()
				.uri("/internal/transacciones/consulta")
				.header(HttpHeaders.AUTHORIZATION, UsuarioAutenticado.cabeceraAutorizacion())
				.body(Map.of("ids", ids.stream().map(TransaccionId::valor).toList()))
				.retrieve()
				.onStatus(estado -> estado.is5xxServerError(), (peticion, error) -> {
					throw new ServicioNoDisponible("movimientos", new IllegalStateException("HTTP " + error.getStatusCode()));
				})
				.body(RespuestaTransaccion[].class);
		return respuesta == null ? List.of() : List.of(respuesta).stream().map(OrigenTransaccionesHttp::hechos).toList();
	}

	private static HechosTransaccion hechos(RespuestaTransaccion respuesta) {
		return new HechosTransaccion(new TransaccionId(respuesta.id()), respuesta.documentoId(), respuesta.concepto());
	}
}
