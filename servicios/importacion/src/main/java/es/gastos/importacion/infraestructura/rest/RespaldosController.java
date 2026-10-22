package es.gastos.importacion.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.importacion.aplicacion.respaldo.RestaurarRespaldo;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restaurar un respaldo v1 (docs/adr/0020). Por defecto solo simula: sustituir todos los datos del
 * usuario tiene que pedirse explícitamente con {@code modo=SUSTITUIR}.
 */
@RestController
class RespaldosController {

	private final RestaurarRespaldo restaurar;

	RespaldosController(RestaurarRespaldo restaurar) {
		this.restaurar = restaurar;
	}

	@PostMapping("/respaldos")
	RestaurarRespaldo.Informe restaurar(@UsuarioActual UsuarioId usuario,
			@RequestParam(defaultValue = "SIMULAR") RestaurarRespaldo.Modo modo, @RequestBody RespaldoV1 respaldo) {
		return this.restaurar.ejecutar(usuario, respaldo, modo);
	}
}
