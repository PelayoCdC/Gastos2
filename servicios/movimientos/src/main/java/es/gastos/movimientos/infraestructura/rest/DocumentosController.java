package es.gastos.movimientos.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.kernel.aplicacion.Pagina;
import es.gastos.kernel.aplicacion.PeticionPagina;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.aplicacion.documentos.GestionDocumentos;
import es.gastos.movimientos.aplicacion.documentos.ResumenDocumento;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Los documentos se crean importando extractos (servicio importacion); aquí se gestionan. */
@RestController
@RequestMapping("/documentos")
class DocumentosController {

	record PeticionNombre(@NotBlank String nombre) {
	}

	record PeticionCuenta(@NotNull UUID cuentaId) {
	}

	private final GestionDocumentos gestion;

	DocumentosController(GestionDocumentos gestion) {
		this.gestion = gestion;
	}

	@GetMapping
	Pagina<ResumenDocumento> listar(@UsuarioActual UsuarioId usuario, @RequestParam(required = false) UUID cuentaId,
			@RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "" + PeticionPagina.TAMANO_POR_DEFECTO) int tamano) {
		return this.gestion.listar(usuario, cuentaId == null ? null : new CuentaId(cuentaId), new PeticionPagina(pagina, tamano));
	}

	@GetMapping("/{id}")
	ResumenDocumento consultar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.gestion.consultar(usuario, new DocumentoId(id));
	}

	@PutMapping("/{id}/nombre")
	ResumenDocumento renombrar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id, @Valid @RequestBody PeticionNombre peticion) {
		DocumentoId documento = new DocumentoId(id);
		this.gestion.renombrar(usuario, documento, peticion.nombre());
		return this.gestion.consultar(usuario, documento);
	}

	/** Mueve el documento y todas sus transacciones a otra cuenta del usuario. */
	@PutMapping("/{id}/cuenta")
	ResumenDocumento reasignar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id, @Valid @RequestBody PeticionCuenta peticion) {
		DocumentoId documento = new DocumentoId(id);
		this.gestion.reasignarCuenta(usuario, documento, new CuentaId(peticion.cuentaId()));
		return this.gestion.consultar(usuario, documento);
	}

	/** Borra el documento y todas sus transacciones. */
	@DeleteMapping("/{id}")
	ResponseEntity<Void> eliminar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		this.gestion.eliminar(usuario, new DocumentoId(id));
		return ResponseEntity.noContent().build();
	}
}
