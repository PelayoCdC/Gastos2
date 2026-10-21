package es.gastos.identidad.infraestructura.rest;

import es.gastos.compartido.web.ManejadorErrores;
import es.gastos.identidad.aplicacion.CredencialesInvalidas;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores propios de identidad: unas credenciales inválidas son un 401, no un 400 de dominio. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ErroresIdentidad {

	@ExceptionHandler(CredencialesInvalidas.class)
	ResponseEntity<ProblemDetail> credencialesInvalidas(CredencialesInvalidas excepcion) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ManejadorErrores.problema(HttpStatus.UNAUTHORIZED, excepcion.codigo(), excepcion.getMessage()));
	}
}
