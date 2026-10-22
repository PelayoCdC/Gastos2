package es.gastos.consultas.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.consultas.aplicacion.informes.ConsultarInformes;
import es.gastos.consultas.dominio.informes.AgregadorBizum;
import es.gastos.consultas.dominio.informes.DetectorRecurrentes;
import es.gastos.consultas.dominio.informes.Informes;
import es.gastos.consultas.dominio.informes.Periodo;
import es.gastos.consultas.dominio.informes.Seleccion;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Informes y análisis. El periodo va en {@code desde}/{@code hasta} (fecha contable, ambos incluidos)
 * y la selección en {@code categoriaId}/{@code etiquetaId} (si llegan las dos, mandan las categorías).
 * Los filtros son ids: el back antiguo filtraba por nombre y tenía que leer a mano los parámetros
 * para que los nombres con comas no se partieran.
 */
@RestController
@RequestMapping("/informes")
class InformesController {

	private final ConsultarInformes informes;

	InformesController(ConsultarInformes informes) {
		this.informes = informes;
	}

	@GetMapping("/resumen-mensual")
	List<Informes.ResumenMes> resumenMensual(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) List<UUID> categoriaId, @RequestParam(required = false) List<UUID> etiquetaId) {
		return this.informes.resumenMensual(usuario, new Periodo(desde, hasta), seleccion(categoriaId, etiquetaId));
	}

	/** Saldo al final de cada día, por cuenta. */
	@GetMapping("/evolucion-saldo")
	List<Informes.SerieSaldo> evolucionSaldo(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return this.informes.evolucionSaldo(usuario, new Periodo(desde, hasta));
	}

	@GetMapping("/gastos-por-categoria")
	List<Informes.TotalCategoria> gastosPorCategoria(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return this.informes.gastosPorCategoria(usuario, new Periodo(desde, hasta));
	}

	/** {@code categoriaId} filtra por la categoría de la etiqueta, no por la de la transacción. */
	@GetMapping("/gastos-por-etiqueta")
	List<Informes.TotalEtiqueta> gastosPorEtiqueta(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) List<UUID> categoriaId) {
		return this.informes.gastosPorEtiqueta(usuario, new Periodo(desde, hasta), categoriaId == null ? null : new HashSet<>(categoriaId));
	}

	@GetMapping("/gasto-diario")
	List<Informes.GastoDia> gastoDiario(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) List<UUID> categoriaId, @RequestParam(required = false) List<UUID> etiquetaId) {
		return this.informes.gastoDiario(usuario, new Periodo(desde, hasta), seleccion(categoriaId, etiquetaId));
	}

	@GetMapping("/gastos-mensuales-por-categoria")
	List<Informes.GastoMesCategoria> gastosMensualesPorCategoria(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return this.informes.gastosMensualesPorCategoria(usuario, new Periodo(desde, hasta));
	}

	@GetMapping("/top-conceptos")
	List<Informes.TopConcepto> topConceptos(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) List<UUID> categoriaId, @RequestParam(required = false) List<UUID> etiquetaId,
			@RequestParam(defaultValue = "" + ConsultarInformes.LIMITE_TOP_POR_DEFECTO) int limite) {
		return this.informes.topConceptos(usuario, new Periodo(desde, hasta), seleccion(categoriaId, etiquetaId), limite);
	}

	/** Siempre sobre todo el histórico. */
	@GetMapping("/recurrentes")
	List<DetectorRecurrentes.Recurrente> recurrentes(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) List<UUID> categoriaId, @RequestParam(required = false) List<UUID> etiquetaId) {
		return this.informes.recurrentes(usuario, seleccion(categoriaId, etiquetaId));
	}

	@GetMapping("/bizum")
	List<AgregadorBizum.ContactoBizum> bizum(@UsuarioActual UsuarioId usuario,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) List<UUID> categoriaId, @RequestParam(required = false) List<UUID> etiquetaId) {
		return this.informes.bizum(usuario, new Periodo(desde, hasta), seleccion(categoriaId, etiquetaId));
	}

	private static Seleccion seleccion(List<UUID> categorias, List<UUID> etiquetas) {
		return new Seleccion(categorias == null ? null : new HashSet<>(categorias), etiquetas == null ? null : new HashSet<>(etiquetas));
	}
}
