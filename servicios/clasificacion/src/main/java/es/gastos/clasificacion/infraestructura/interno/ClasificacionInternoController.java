package es.gastos.clasificacion.infraestructura.interno;

import es.gastos.clasificacion.aplicacion.respaldo.RestaurarClasificacion;
import es.gastos.clasificacion.dominio.respaldo.PlanRestauracion;
import es.gastos.compartido.web.UsuarioActual;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API interna para otros servicios (el gateway nunca la expone), con el JWT del usuario reenviado.
 * Hoy solo la parte de clasificacion de la restauración de respaldos, que orquesta importacion
 * (docs/adr/0020).
 */
@RestController
@RequestMapping("/internal")
class ClasificacionInternoController {

	record PeticionRespaldo(List<PlanRestauracion.CategoriaRespaldo> categorias, List<PlanRestauracion.EtiquetaRespaldo> etiquetas,
			List<PlanRestauracion.TransaccionRespaldo> transacciones) {
	}

	private final RestaurarClasificacion restaurar;

	ClasificacionInternoController(RestaurarClasificacion restaurar) {
		this.restaurar = restaurar;
	}

	/** Con {@code simular=true} solo informa. {@code PUT} porque sustituye y repetirlo da el mismo resultado. */
	@PutMapping("/respaldos/v1")
	RestaurarClasificacion.Resumen restaurar(@UsuarioActual UsuarioId usuario, @RequestParam(defaultValue = "false") boolean simular,
			@RequestBody PeticionRespaldo peticion) {
		return this.restaurar.ejecutar(new RestaurarClasificacion.Comando(usuario, peticion.categorias(), peticion.etiquetas(),
				peticion.transacciones(), simular));
	}
}
