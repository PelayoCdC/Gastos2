package es.gastos.clasificacion.infraestructura.rest;

import es.gastos.clasificacion.aplicacion.clasificacion.ClasificacionAutomatica;
import es.gastos.clasificacion.aplicacion.clasificacion.ClasificarTransacciones;
import es.gastos.clasificacion.aplicacion.clasificacion.DatosClasificacion;
import es.gastos.clasificacion.dominio.clasificacion.ModoAutomatico;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.compartido.web.UsuarioActual;
import es.gastos.kernel.dominio.UsuarioId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Clasificación de transacciones: manual, propagación, en bloque y automática. */
@RestController
class ClasificacionController {

	record PeticionClasificacion(UUID categoriaId, String nuevaCategoria, List<UUID> etiquetaIds, List<String> nuevasEtiquetas) {

		ClasificarTransacciones.Peticion peticion() {
			return new ClasificarTransacciones.Peticion(this.categoriaId, this.nuevaCategoria, this.etiquetaIds, this.nuevasEtiquetas);
		}
	}

	enum ModoEtiquetas {
		ANADIR,
		SUSTITUIR
	}

	record PeticionEnBloque(@NotNull List<UUID> transaccionIds, UUID categoriaId, String nuevaCategoria, List<UUID> etiquetaIds,
			List<String> nuevasEtiquetas, ModoEtiquetas modoEtiquetas) {
	}

	/** {@code forzar} es opcional: un primitivo ausente sería un 400 con Jackson 3. */
	record PeticionAutomatica(@NotNull ModoAutomatico modo, Boolean forzar) {

		boolean forzarReinicio() {
			return Boolean.TRUE.equals(this.forzar);
		}
	}

	record Actualizadas(int actualizadas) {
	}

	private final ClasificarTransacciones clasificar;

	private final ClasificacionAutomatica automatica;

	ClasificacionController(ClasificarTransacciones clasificar, ClasificacionAutomatica automatica) {
		this.clasificar = clasificar;
		this.automatica = automatica;
	}

	@GetMapping("/transacciones/{id}")
	DatosClasificacion consultar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.clasificar.consultar(usuario, new TransaccionId(id));
	}

	/** Fija la clasificación completa: la categoría y las etiquetas indicadas sustituyen a las actuales. */
	@PutMapping("/transacciones/{id}")
	DatosClasificacion asignar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id, @RequestBody PeticionClasificacion peticion) {
		return this.clasificar.asignar(usuario, new TransaccionId(id), peticion.peticion());
	}

	@GetMapping("/transacciones/{id}/similares")
	ClasificarTransacciones.Similares similares(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.clasificar.similares(usuario, new TransaccionId(id));
	}

	@PostMapping("/transacciones/{id}/propagacion")
	Actualizadas propagar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return new Actualizadas(this.clasificar.propagar(usuario, new TransaccionId(id)));
	}

	@PostMapping("/aplicacion-masiva")
	ClasificarTransacciones.ResultadoBloque enBloque(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionEnBloque peticion) {
		return this.clasificar.aplicarEnBloque(usuario, peticion.transaccionIds(),
				new ClasificarTransacciones.Peticion(peticion.categoriaId(), peticion.nuevaCategoria(), peticion.etiquetaIds(),
						peticion.nuevasEtiquetas()),
				peticion.modoEtiquetas() == ModoEtiquetas.SUSTITUIR);
	}

	/** Reaplica las reglas a todo el histórico (ETIQUETAS, CATEGORIAS o REINICIAR, este último con {@code forzar}). */
	@PostMapping("/automatica")
	ClasificacionAutomatica.Resultado automatica(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionAutomatica peticion) {
		return this.automatica.ejecutar(usuario, peticion.modo(), peticion.forzarReinicio());
	}
}
