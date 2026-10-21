package es.gastos.movimientos.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.aplicacion.cuentas.EliminarCuenta;
import es.gastos.movimientos.aplicacion.cuentas.GestionCuentas;
import es.gastos.movimientos.aplicacion.cuentas.ResumenCuenta;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cuentas")
class CuentasController {

	record PeticionCuenta(@NotBlank String nombre, String banco, String descripcion) {

		GestionCuentas.DatosCuenta datos() {
			return new GestionCuentas.DatosCuenta(this.nombre, this.banco, this.descripcion);
		}
	}

	private final GestionCuentas gestion;

	private final EliminarCuenta eliminarCuenta;

	CuentasController(GestionCuentas gestion, EliminarCuenta eliminarCuenta) {
		this.gestion = gestion;
		this.eliminarCuenta = eliminarCuenta;
	}

	@GetMapping
	List<ResumenCuenta> listar(@UsuarioActual UsuarioId usuario) {
		return this.gestion.listar(usuario);
	}

	@GetMapping("/{id}")
	ResumenCuenta consultar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.gestion.consultar(usuario, new CuentaId(id));
	}

	@PostMapping
	ResponseEntity<ResumenCuenta> crear(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionCuenta peticion) {
		CuentaId id = this.gestion.crear(usuario, peticion.datos());
		return ResponseEntity.created(URI.create("/cuentas/" + id)).body(this.gestion.consultar(usuario, id));
	}

	@PutMapping("/{id}")
	ResumenCuenta modificar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id, @Valid @RequestBody PeticionCuenta peticion) {
		CuentaId cuenta = new CuentaId(id);
		this.gestion.modificar(usuario, cuenta, peticion.datos());
		return this.gestion.consultar(usuario, cuenta);
	}

	/** Con movimientos responde 409 salvo que se pida {@code conMovimientos=true} (borrado en cascada). */
	@DeleteMapping("/{id}")
	ResponseEntity<Void> eliminar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id,
			@RequestParam(defaultValue = "false") boolean conMovimientos) {
		this.eliminarCuenta.ejecutar(usuario, new CuentaId(id), conMovimientos);
		return ResponseEntity.noContent().build();
	}
}
