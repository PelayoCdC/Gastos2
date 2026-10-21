package es.gastos.movimientos.infraestructura.interno;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.aplicacion.extractos.RegistrarExtracto;
import es.gastos.movimientos.aplicacion.extractos.ResultadoExtracto;
import es.gastos.movimientos.aplicacion.transacciones.DatosTransaccion;
import es.gastos.movimientos.aplicacion.transacciones.GestionTransacciones;
import es.gastos.movimientos.dominio.transaccion.TransaccionId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API interna para otros servicios (el gateway nunca la expone). Las llamadas llevan el JWT del
 * usuario reenviado por el servicio que llama, así que la propiedad de los datos se comprueba igual
 * que en la API pública.
 */
@RestController
@RequestMapping("/internal")
class MovimientosInternoController {

	/** Extracto ya interpretado por importacion (contrato cliente/proveedor, docs/adr/0003). */
	record PeticionExtracto(@NotNull UUID extractoId, @NotNull UUID cuentaId, String nombre, @NotBlank String nombreArchivo,
			@NotNull List<@Valid LineaPeticion> lineas) {
	}

	record LineaPeticion(int fila, @NotNull LocalDate fechaContable, LocalDate fechaValor, String concepto,
			@NotNull BigDecimal importe, BigDecimal saldo) {
	}

	record PeticionConsulta(@NotNull List<UUID> ids) {
	}

	private final RegistrarExtracto registrarExtracto;

	private final GestionTransacciones transacciones;

	MovimientosInternoController(RegistrarExtracto registrarExtracto, GestionTransacciones transacciones) {
		this.registrarExtracto = registrarExtracto;
		this.transacciones = transacciones;
	}

	@PostMapping("/extractos")
	ResponseEntity<ResultadoExtracto> registrar(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionExtracto peticion) {
		ResultadoExtracto resultado = this.registrarExtracto.ejecutar(new RegistrarExtracto.Comando(usuario,
				peticion.extractoId(), peticion.cuentaId(), peticion.nombre(), peticion.nombreArchivo(),
				peticion.lineas().stream().map(l -> new RegistrarExtracto.Linea(l.fila(), l.fechaContable(), l.fechaValor(),
						l.concepto(), l.importe(), l.saldo())).toList()));
		return ResponseEntity.created(URI.create("/documentos/" + resultado.documentoId())).body(resultado);
	}

	/** Para el read-through de clasificacion: los datos de una transacción recién creada. */
	@GetMapping("/transacciones/{id}")
	DatosTransaccion transaccion(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.transacciones.consultar(usuario, new TransaccionId(id));
	}

	@PostMapping("/transacciones/consulta")
	List<DatosTransaccion> transacciones(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionConsulta peticion) {
		return this.transacciones.consultarVarias(usuario, peticion.ids().stream().map(TransaccionId::new).toList());
	}
}
