package es.gastos.consultas.dominio.transacciones;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Normalizador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Filtros de la rejilla de transacciones. Todos son opcionales; los nulos o vacíos no filtran.
 *
 * <ul>
 *   <li>{@code concepto}: contiene el texto, sin distinguir mayúsculas ni tildes ("cafeteria" encuentra
 *   "CAFETERÍA").</li>
 *   <li>{@code desde}/{@code hasta} (fecha contable) e {@code importeMin}/{@code importeMax}: ambos
 *   extremos incluidos.</li>
 *   <li>Categorías: "tiene una de {@code categorias}" <b>o</b>, si {@code sinCategoria}, "no tiene
 *   categoría". Etiquetas igual, con al menos una de las etiquetas pedidas. Así el usuario puede pedir
 *   "Ocio o sin clasificar" en una sola consulta, como en el back antiguo.</li>
 *   <li>Los filtros van por id, no por nombre (docs/adr/0009): un nombre con comas ya no se parte.</li>
 * </ul>
 */
public record FiltroTransacciones(String concepto, LocalDate desde, LocalDate hasta, BigDecimal importeMin, BigDecimal importeMax,
		UUID cuentaId, UUID documentoId, Set<UUID> categorias, boolean sinCategoria, Set<UUID> etiquetas, boolean sinEtiqueta) {

	public static final FiltroTransacciones NINGUNO = new FiltroTransacciones(null, null, null, null, null, null, null, Set.of(),
			false, Set.of(), false);

	public FiltroTransacciones {
		concepto = concepto == null || concepto.isBlank() ? null : concepto.strip();
		categorias = categorias == null ? Set.of() : Set.copyOf(categorias);
		etiquetas = etiquetas == null ? Set.of() : Set.copyOf(etiquetas);
		if (desde != null && hasta != null && desde.isAfter(hasta)) {
			throw new ExcepcionDominio("FILTRO_INVALIDO", "La fecha 'desde' es posterior a 'hasta'");
		}
		if (importeMin != null && importeMax != null && importeMin.compareTo(importeMax) > 0) {
			throw new ExcepcionDominio("FILTRO_INVALIDO", "El importe minimo es mayor que el maximo");
		}
	}

	/** El texto a buscar en su forma normalizada, la misma con la que se guarda el concepto. */
	public String conceptoNormalizado() {
		return this.concepto == null ? null : Normalizador.normalizar(this.concepto);
	}

	public boolean filtraCategorias() {
		return !this.categorias.isEmpty() || this.sinCategoria;
	}

	public boolean filtraEtiquetas() {
		return !this.etiquetas.isEmpty() || this.sinEtiqueta;
	}
}
