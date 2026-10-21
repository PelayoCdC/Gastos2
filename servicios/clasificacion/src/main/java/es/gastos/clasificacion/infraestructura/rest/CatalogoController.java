package es.gastos.clasificacion.infraestructura.rest;

import es.gastos.clasificacion.aplicacion.catalogo.DefinicionesCatalogo;
import es.gastos.clasificacion.aplicacion.catalogo.EliminarDelCatalogo;
import es.gastos.clasificacion.aplicacion.catalogo.GestionCategorias;
import es.gastos.clasificacion.aplicacion.catalogo.GestionEtiquetas;
import es.gastos.clasificacion.aplicacion.catalogo.ReordenarCatalogo;
import es.gastos.clasificacion.aplicacion.catalogo.VistasCatalogo.DatosCategoria;
import es.gastos.clasificacion.aplicacion.catalogo.VistasCatalogo.DatosEtiqueta;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.compartido.web.UsuarioActual;
import es.gastos.kernel.dominio.UsuarioId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Catálogo del usuario: categorías, etiquetas, su orden de prioridad y sus definiciones. */
@RestController
class CatalogoController {

	record PeticionCategoria(@NotBlank String nombre, String color, String descripcion, List<String> ocurrencias) {
	}

	record PeticionEtiqueta(@NotBlank String nombre, String descripcion, List<String> ocurrencias, UUID categoriaId) {
	}

	record PeticionOrden(@NotNull List<UUID> ids) {
	}

	private final GestionCategorias categorias;

	private final GestionEtiquetas etiquetas;

	private final EliminarDelCatalogo eliminar;

	private final ReordenarCatalogo reordenar;

	private final DefinicionesCatalogo definiciones;

	CatalogoController(GestionCategorias categorias, GestionEtiquetas etiquetas, EliminarDelCatalogo eliminar,
			ReordenarCatalogo reordenar, DefinicionesCatalogo definiciones) {
		this.categorias = categorias;
		this.etiquetas = etiquetas;
		this.eliminar = eliminar;
		this.reordenar = reordenar;
		this.definiciones = definiciones;
	}

	@GetMapping("/categorias")
	List<DatosCategoria> categorias(@UsuarioActual UsuarioId usuario) {
		return this.categorias.listar(usuario);
	}

	@GetMapping("/categorias/{id}")
	DatosCategoria categoria(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.categorias.consultar(usuario, new CategoriaId(id));
	}

	@PostMapping("/categorias")
	ResponseEntity<DatosCategoria> crearCategoria(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionCategoria peticion) {
		DatosCategoria creada = this.categorias.crear(usuario, datos(peticion));
		return ResponseEntity.created(URI.create("/categorias/" + creada.id())).body(creada);
	}

	@PutMapping("/categorias/{id}")
	DatosCategoria modificarCategoria(@UsuarioActual UsuarioId usuario, @PathVariable UUID id,
			@Valid @RequestBody PeticionCategoria peticion) {
		return this.categorias.modificar(usuario, new CategoriaId(id), datos(peticion));
	}

	/** La desasigna de transacciones y etiquetas antes de borrarla. */
	@DeleteMapping("/categorias/{id}")
	ResponseEntity<Void> eliminarCategoria(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		this.eliminar.eliminarCategoria(usuario, new CategoriaId(id));
		return ResponseEntity.noContent().build();
	}

	/** Nuevo orden de prioridad: la lista completa de ids, de más a menos prioritaria. */
	@PutMapping("/categorias/orden")
	List<DatosCategoria> reordenarCategorias(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionOrden peticion) {
		this.reordenar.categorias(usuario, peticion.ids());
		return this.categorias.listar(usuario);
	}

	@GetMapping("/etiquetas")
	List<DatosEtiqueta> etiquetas(@UsuarioActual UsuarioId usuario, @RequestParam(name = "categoriaId", required = false) List<UUID> categoriaIds,
			@RequestParam(defaultValue = "false") boolean sinCategoria) {
		return this.etiquetas.listar(usuario, new GestionEtiquetas.Filtro(categoriaIds == null ? List.of() : categoriaIds, sinCategoria));
	}

	@GetMapping("/etiquetas/{id}")
	DatosEtiqueta etiqueta(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.etiquetas.consultar(usuario, new EtiquetaId(id));
	}

	@PostMapping("/etiquetas")
	ResponseEntity<DatosEtiqueta> crearEtiqueta(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionEtiqueta peticion) {
		DatosEtiqueta creada = this.etiquetas.crear(usuario, datos(peticion));
		return ResponseEntity.created(URI.create("/etiquetas/" + creada.id())).body(creada);
	}

	@PutMapping("/etiquetas/{id}")
	DatosEtiqueta modificarEtiqueta(@UsuarioActual UsuarioId usuario, @PathVariable UUID id,
			@Valid @RequestBody PeticionEtiqueta peticion) {
		return this.etiquetas.modificar(usuario, new EtiquetaId(id), datos(peticion));
	}

	@DeleteMapping("/etiquetas/{id}")
	ResponseEntity<Void> eliminarEtiqueta(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		this.eliminar.eliminarEtiqueta(usuario, new EtiquetaId(id));
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/etiquetas/orden")
	List<DatosEtiqueta> reordenarEtiquetas(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionOrden peticion) {
		this.reordenar.etiquetas(usuario, peticion.ids());
		return this.etiquetas.listar(usuario, GestionEtiquetas.Filtro.TODAS);
	}

	@GetMapping("/definiciones")
	DefinicionesCatalogo.Definiciones exportar(@UsuarioActual UsuarioId usuario) {
		return this.definiciones.exportar(usuario);
	}

	@PostMapping("/definiciones")
	DefinicionesCatalogo.Resultado importar(@UsuarioActual UsuarioId usuario, @RequestBody DefinicionesCatalogo.Definiciones definiciones) {
		return this.definiciones.importar(usuario, definiciones);
	}

	private static GestionCategorias.Datos datos(PeticionCategoria peticion) {
		return new GestionCategorias.Datos(peticion.nombre(), peticion.color(), peticion.descripcion(), peticion.ocurrencias());
	}

	private static GestionEtiquetas.Datos datos(PeticionEtiqueta peticion) {
		return new GestionEtiquetas.Datos(peticion.nombre(), peticion.descripcion(), peticion.ocurrencias(), peticion.categoriaId());
	}
}
