package es.gastos.consultas.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.consultas.aplicacion.exportacion.ExportarRespaldo;
import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Respaldo completo en formato v1 (docs/adr/0020), compatible con el back antiguo y la app móvil. Se
 * restaura con {@code POST /api/importacion/respaldos}.
 */
@RestController
class ExportacionController {

	private final ExportarRespaldo exportar;

	ExportacionController(ExportarRespaldo exportar) {
		this.exportar = exportar;
	}

	@GetMapping("/exportacion/v1")
	ResponseEntity<RespaldoV1> exportar(@UsuarioActual UsuarioId usuario) {
		RespaldoV1 respaldo = this.exportar.exportar(usuario);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("gastos-respaldo-" + respaldo.generadoEn() + ".json").build().toString())
				.body(respaldo);
	}
}
