package es.gastos.consultas.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.consultas.aplicacion.consulta.ConsultarTransacciones;
import es.gastos.consultas.aplicacion.consulta.FilaTransaccion;
import es.gastos.consultas.dominio.transacciones.FiltroTransacciones;
import es.gastos.consultas.dominio.transacciones.Ordenacion;
import es.gastos.consultas.infraestructura.xlsx.ExportadorXlsx;
import es.gastos.kernel.aplicacion.Pagina;
import es.gastos.kernel.aplicacion.PeticionPagina;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Rejilla, detalle y exportación de transacciones. Todo es {@code GET} con los filtros en la query
 * (antes era {@code POST /search} con el criterio en el cuerpo): se puede cachear, enlazar y repetir.
 * Las listas ({@code categoriaId}, {@code etiquetaId}) se repiten o van separadas por comas.
 */
@RestController
class TransaccionesController {

	private final ConsultarTransacciones consultar;

	private final ExportadorXlsx exportador;

	private final Clock reloj;

	TransaccionesController(ConsultarTransacciones consultar, ExportadorXlsx exportador, Clock reloj) {
		this.consultar = consultar;
		this.exportador = exportador;
		this.reloj = reloj;
	}

	@GetMapping("/transacciones")
	Pagina<FilaTransaccion> buscar(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) String concepto,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) BigDecimal importeMin,
			@RequestParam(required = false) BigDecimal importeMax,
			@RequestParam(required = false) UUID cuentaId,
			@RequestParam(required = false) UUID documentoId,
			@RequestParam(required = false) List<UUID> categoriaId,
			@RequestParam(required = false) Boolean sinCategoria,
			@RequestParam(required = false) List<UUID> etiquetaId,
			@RequestParam(required = false) Boolean sinEtiqueta,
			@RequestParam(required = false) String orden,
			@RequestParam(required = false) String direccion,
			@RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "" + PeticionPagina.TAMANO_POR_DEFECTO) int tamano) {
		return this.consultar.buscar(usuario, filtro(concepto, desde, hasta, importeMin, importeMax, cuentaId, documentoId, categoriaId,
				sinCategoria, etiquetaId, sinEtiqueta), Ordenacion.de(orden, direccion), new PeticionPagina(pagina, tamano));
	}

	@GetMapping("/transacciones/{id}")
	FilaTransaccion detalle(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.consultar.detalle(usuario, id);
	}

	/** Los mismos filtros y orden que la rejilla, sin paginar. */
	@GetMapping("/transacciones/exportacion")
	ResponseEntity<byte[]> exportar(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) String concepto,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) BigDecimal importeMin,
			@RequestParam(required = false) BigDecimal importeMax,
			@RequestParam(required = false) UUID cuentaId,
			@RequestParam(required = false) UUID documentoId,
			@RequestParam(required = false) List<UUID> categoriaId,
			@RequestParam(required = false) Boolean sinCategoria,
			@RequestParam(required = false) List<UUID> etiquetaId,
			@RequestParam(required = false) Boolean sinEtiqueta,
			@RequestParam(required = false) String orden,
			@RequestParam(required = false) String direccion) {
		List<FilaTransaccion> filas = this.consultar.paraExportar(usuario, filtro(concepto, desde, hasta, importeMin, importeMax,
				cuentaId, documentoId, categoriaId, sinCategoria, etiquetaId, sinEtiqueta), Ordenacion.de(orden, direccion));
		String nombre = "transacciones-" + LocalDate.now(this.reloj) + ".xlsx";
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(ExportadorXlsx.TIPO))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
				.body(this.exportador.exportar(filas));
	}

	private static FiltroTransacciones filtro(String concepto, LocalDate desde, LocalDate hasta, BigDecimal importeMin,
			BigDecimal importeMax, UUID cuentaId, UUID documentoId, List<UUID> categorias, Boolean sinCategoria, List<UUID> etiquetas,
			Boolean sinEtiqueta) {
		return new FiltroTransacciones(concepto, desde, hasta, importeMin, importeMax, cuentaId, documentoId,
				categorias == null ? null : new HashSet<>(categorias), Boolean.TRUE.equals(sinCategoria),
				etiquetas == null ? null : new HashSet<>(etiquetas), Boolean.TRUE.equals(sinEtiqueta));
	}
}
