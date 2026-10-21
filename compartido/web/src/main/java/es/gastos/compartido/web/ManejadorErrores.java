package es.gastos.compartido.web;

import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Traduce las excepciones a respuestas RFC 9457 ({@code application/problem+json}) con un
 * {@code codigo} estable que el cliente puede usar para decidir qué hacer. Ver
 * docs/arquitectura/05-conceptos-transversales.md.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

	public record ErrorCampo(String campo, String mensaje) {
	}

	@ExceptionHandler(ExcepcionDominio.class)
	ResponseEntity<ProblemDetail> excepcionDeDominio(ExcepcionDominio excepcion) {
		HttpStatus estado = switch (excepcion) {
			case RecursoNoEncontrado ignored -> HttpStatus.NOT_FOUND;
			case Conflicto ignored -> HttpStatus.CONFLICT;
			default -> HttpStatus.BAD_REQUEST;
		};
		return ResponseEntity.status(estado).body(problema(estado, excepcion.codigo(), excepcion.getMessage()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ProblemDetail> parametroInvalido(MethodArgumentTypeMismatchException excepcion) {
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
				"Valor no valido para '" + excepcion.getName() + "'");
		problema.setProperty("errores", List.of(new ErrorCampo(excepcion.getName(), "valor no valido")));
		return ResponseEntity.badRequest().body(problema);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException excepcion,
			HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
		List<ErrorCampo> errores = excepcion.getBindingResult().getFieldErrors().stream()
				.map(error -> new ErrorCampo(error.getField(), mensaje(error)))
				.toList();
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "VALIDACION", "La peticion tiene campos no validos");
		problema.setProperty("errores", errores);
		return ResponseEntity.badRequest().body(problema);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException excepcion,
			HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
		return ResponseEntity.badRequest()
				.body(problema(HttpStatus.BAD_REQUEST, "PETICION_MALFORMADA", "El cuerpo de la peticion no es valido"));
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException excepcion,
			HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
				.body(problema(HttpStatus.CONTENT_TOO_LARGE, "FICHERO_DEMASIADO_GRANDE", "El fichero supera el tamano maximo"));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException excepcion,
			HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_NO_SOPORTADO", "Tipo de contenido no soportado"));
	}

	/** El resto de errores estándar de Spring también llevan {@code codigo}, derivado del estado. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception excepcion, Object cuerpo, HttpHeaders cabeceras,
			HttpStatusCode estado, WebRequest peticion) {
		if (cuerpo instanceof ProblemDetail problema && (problema.getProperties() == null || !problema.getProperties().containsKey("codigo"))) {
			String codigo = HttpStatus.valueOf(estado.value()).name();
			problema.setProperty("codigo", codigo);
			problema.setType(tipo(codigo));
		}
		return super.handleExceptionInternal(excepcion, cuerpo, cabeceras, estado, peticion);
	}

	public static ProblemDetail problema(HttpStatus estado, String codigo, String detalle) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
		problema.setType(tipo(codigo));
		problema.setTitle(estado.getReasonPhrase());
		problema.setProperties(new java.util.LinkedHashMap<>(Map.of("codigo", codigo)));
		return problema;
	}

	private static URI tipo(String codigo) {
		return URI.create("urn:gastos:problema:" + codigo.toLowerCase(Locale.ROOT).replace('_', '-'));
	}

	private static String mensaje(FieldError error) {
		return error.getDefaultMessage() == null ? "valor no valido" : error.getDefaultMessage();
	}
}
