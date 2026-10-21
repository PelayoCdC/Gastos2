package es.gastos.movimientos.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.aplicacion.transacciones.DatosTransaccion;
import es.gastos.movimientos.aplicacion.transacciones.GestionTransacciones;
import es.gastos.movimientos.dominio.transaccion.TransaccionId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hechos de las transacciones. La categoría y las etiquetas se asignan en
 * {@code /api/clasificacion/transacciones/{id}}; la rejilla con todo combinado está en consultas.
 */
@RestController
@RequestMapping("/transacciones")
class TransaccionesController {

	record PeticionTransaccion(UUID cuentaId, @NotNull LocalDate fechaContable, LocalDate fechaValor, String concepto,
			String nota, @NotNull BigDecimal importe) {

		GestionTransacciones.Hechos hechos() {
			return new GestionTransacciones.Hechos(this.cuentaId, this.fechaContable, this.fechaValor, this.concepto,
					this.nota, this.importe);
		}
	}

	private final GestionTransacciones gestion;

	TransaccionesController(GestionTransacciones gestion) {
		this.gestion = gestion;
	}

	@PostMapping
	ResponseEntity<DatosTransaccion> registrar(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionTransaccion peticion) {
		DatosTransaccion creada = this.gestion.registrarManual(usuario, peticion.hechos());
		return ResponseEntity.created(URI.create("/transacciones/" + creada.id())).body(creada);
	}

	@GetMapping("/{id}")
	DatosTransaccion consultar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.gestion.consultar(usuario, new TransaccionId(id));
	}

	@PutMapping("/{id}")
	DatosTransaccion corregir(@UsuarioActual UsuarioId usuario, @PathVariable UUID id,
			@Valid @RequestBody PeticionTransaccion peticion) {
		return this.gestion.corregir(usuario, new TransaccionId(id), peticion.hechos());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> eliminar(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		this.gestion.eliminar(usuario, new TransaccionId(id));
		return ResponseEntity.noContent().build();
	}
}
