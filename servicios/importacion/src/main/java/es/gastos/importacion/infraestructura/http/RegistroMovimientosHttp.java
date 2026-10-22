package es.gastos.importacion.infraestructura.http;

import es.gastos.compartido.mensajeria.SecuenciaPublicada;
import es.gastos.compartido.web.UsuarioAutenticado;
import es.gastos.importacion.aplicacion.RegistroMovimientos;
import es.gastos.importacion.dominio.extracto.Interpretacion;
import es.gastos.kernel.aplicacion.ServicioNoDisponible;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador del puerto {@link RegistroMovimientos} contra {@code POST /internal/extractos} de
 * movimientos. Reenvía el JWT del usuario, así movimientos comprueba que la cuenta es suya con la
 * misma regla que en su API pública (una cuenta ajena da 404, docs/adr/0008).
 *
 * <p>Los fallos de red se reintentan dos veces: es seguro porque el {@code extractoId} hace la
 * llamada idempotente (si la primera llegó a confirmarse, el reintento devuelve el mismo resultado
 * sin importar nada). Los errores de negocio de movimientos se traducen a las mismas excepciones del
 * kernel, de modo que el cliente recibe el mismo {@code codigo} que habría dado movimientos.
 */
@Component
class RegistroMovimientosHttp implements RegistroMovimientos {

	record PeticionExtracto(UUID extractoId, UUID cuentaId, String nombre, String nombreArchivo, List<LineaPeticion> lineas) {
	}

	record LineaPeticion(int fila, LocalDate fechaContable, LocalDate fechaValor, String concepto, BigDecimal importe,
			BigDecimal saldo) {
	}

	record RespuestaExtracto(UUID documentoId, String nombreDocumento, String nombreArchivo, int importadas,
			List<Duplicada> duplicados) {
	}

	private final RestClient cliente;

	RegistroMovimientosHttp(RestClient.Builder constructor, @Value("${gastos.servicios.movimientos}") String movimientos) {
		this.cliente = constructor.baseUrl(movimientos).build();
	}

	@Override
	@Retryable(includes = ResourceAccessException.class, maxRetries = 2, delay = 200)
	public Registro registrar(UsuarioId usuario, Extracto extracto) {
		String token = UsuarioAutenticado.cabeceraAutorizacion();
		ResponseEntity<RespuestaExtracto> entidad;
		try {
			entidad = this.cliente.post()
					.uri("/internal/extractos")
					.header(HttpHeaders.AUTHORIZATION, token)
					.body(peticion(extracto))
					.retrieve()
					.toEntity(RespuestaExtracto.class);
		} catch (HttpClientErrorException.NotFound noExiste) {
			throw new RecursoNoEncontrado("la cuenta", extracto.cuentaId());
		} catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Conflict rechazo) {
			throw traducir(rechazo);
		} catch (ResourceAccessException sinRed) {
			// Se deja pasar tal cual para que @Retryable reintente; si se agotan, la capa web da 503
			throw sinRed;
		} catch (RuntimeException e) {
			throw new ServicioNoDisponible("movimientos", e);
		}
		RespuestaExtracto respuesta = entidad.getBody();
		if (respuesta == null) {
			throw new ServicioNoDisponible("movimientos", new IllegalStateException("Respuesta vacia"));
		}
		// Los eventos de la importación los escribe movimientos: su secuencia es la que el cliente debe esperar
		SecuenciaPublicada.registrarCabecera(entidad.getHeaders().getFirst(SecuenciaPublicada.CABECERA));
		return new Registro(respuesta.documentoId(), respuesta.nombreDocumento(), respuesta.nombreArchivo(), respuesta.importadas(),
				respuesta.duplicados() == null ? List.of() : respuesta.duplicados());
	}

	private static PeticionExtracto peticion(Extracto extracto) {
		return new PeticionExtracto(extracto.extractoId(), extracto.cuentaId(), extracto.nombre(), extracto.nombreArchivo(),
				extracto.lineas().stream().map(RegistroMovimientosHttp::linea).toList());
	}

	private static LineaPeticion linea(Interpretacion.Linea linea) {
		return new LineaPeticion(linea.fila(), linea.fechaContable(), linea.fechaValor(), linea.concepto(), valor(linea.importe()),
				valor(linea.saldo()));
	}

	private static BigDecimal valor(Importe importe) {
		return importe == null ? null : importe.valor();
	}

	/** Conserva el {@code codigo} y el mensaje del ProblemDetail de movimientos. */
	@SuppressWarnings("unchecked")
	private static ExcepcionDominio traducir(RestClientResponseException rechazo) {
		Map<String, Object> problema = rechazo.getResponseBodyAs(Map.class);
		String codigo = problema == null || problema.get("codigo") == null ? "EXTRACTO_RECHAZADO" : problema.get("codigo").toString();
		String detalle = problema == null || problema.get("detail") == null
				? "movimientos rechazo el extracto" : problema.get("detail").toString();
		return rechazo.getStatusCode().value() == 409 ? new Conflicto(codigo, detalle) : new ExcepcionDominio(codigo, detalle);
	}
}
