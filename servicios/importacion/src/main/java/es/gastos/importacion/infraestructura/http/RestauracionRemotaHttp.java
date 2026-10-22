package es.gastos.importacion.infraestructura.http;

import es.gastos.compartido.mensajeria.SecuenciaPublicada;
import es.gastos.compartido.web.UsuarioAutenticado;
import es.gastos.importacion.aplicacion.respaldo.RestauracionRemota;
import es.gastos.importacion.dominio.respaldo.RespaldoTraducido;
import es.gastos.kernel.aplicacion.ServicioNoDisponible;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Reparte un respaldo a {@code PUT /internal/respaldos/v1} de movimientos y de clasificacion, con el JWT
 * del usuario reenviado. Las partes de {@link RespaldoTraducido} ya tienen los nombres de campo que
 * esperan los dos servicios (los campos que no son suyos se ignoran al leer).
 *
 * <p>Se reintenta ante fallos de red porque la operación sustituye: repetirla deja el mismo estado. La
 * {@code Gastos-Secuencia} de cada servicio se reenvía al cliente, que es lo que tiene que esperar en
 * consultas.
 */
@Component
class RestauracionRemotaHttp implements RestauracionRemota.Movimientos, RestauracionRemota.Clasificacion {

	private final RestClient movimientos;

	private final RestClient clasificacion;

	RestauracionRemotaHttp(RestClient.Builder constructor, @Value("${gastos.servicios.movimientos}") String movimientos,
			@Value("${gastos.servicios.clasificacion}") String clasificacion) {
		this.movimientos = constructor.clone().baseUrl(movimientos).build();
		this.clasificacion = constructor.clone().baseUrl(clasificacion).build();
	}

	@Override
	@Retryable(includes = ResourceAccessException.class, maxRetries = 2, delay = 500)
	public RestauracionRemota.ResumenMovimientos restaurarMovimientos(UsuarioId usuario, RespaldoTraducido respaldo, boolean simular) {
		return enviar(this.movimientos, "movimientos", simular, Map.of("cuentas", respaldo.cuentas(), "documentos", respaldo.documentos(),
				"transacciones", respaldo.transacciones()), RestauracionRemota.ResumenMovimientos.class);
	}

	@Override
	@Retryable(includes = ResourceAccessException.class, maxRetries = 2, delay = 500)
	public RestauracionRemota.ResumenClasificacion restaurarClasificacion(UsuarioId usuario, RespaldoTraducido respaldo, boolean simular) {
		return enviar(this.clasificacion, "clasificacion", simular, Map.of("categorias", respaldo.categorias(), "etiquetas",
				respaldo.etiquetas(), "transacciones", respaldo.transacciones()), RestauracionRemota.ResumenClasificacion.class);
	}

	private static <T> T enviar(RestClient cliente, String servicio, boolean simular, Map<String, Object> cuerpo, Class<T> tipo) {
		String token = UsuarioAutenticado.cabeceraAutorizacion();
		ResponseEntity<T> respuesta;
		try {
			respuesta = cliente.put()
					.uri(uri -> uri.path("/internal/respaldos/v1").queryParam("simular", simular).build())
					.header(HttpHeaders.AUTHORIZATION, token)
					.body(cuerpo)
					.retrieve()
					.toEntity(tipo);
		} catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Conflict rechazo) {
			throw traducir(rechazo);
		} catch (ResourceAccessException sinRed) {
			throw sinRed;
		} catch (RuntimeException e) {
			throw new ServicioNoDisponible(servicio, e);
		}
		if (respuesta.getBody() == null) {
			throw new ServicioNoDisponible(servicio, new IllegalStateException("Respuesta vacia"));
		}
		SecuenciaPublicada.registrarCabecera(respuesta.getHeaders().getFirst(SecuenciaPublicada.CABECERA));
		return respuesta.getBody();
	}

	@SuppressWarnings("unchecked")
	private static ExcepcionDominio traducir(RestClientResponseException rechazo) {
		Map<String, Object> problema = rechazo.getResponseBodyAs(Map.class);
		String codigo = problema == null || problema.get("codigo") == null ? "RESPALDO_RECHAZADO" : problema.get("codigo").toString();
		String detalle = problema == null || problema.get("detail") == null ? "El respaldo no es valido" : problema.get("detail").toString();
		return rechazo.getStatusCode().value() == 409 ? new Conflicto(codigo, detalle) : new ExcepcionDominio(codigo, detalle);
	}
}
