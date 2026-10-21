package es.gastos.clasificacion.dominio.clasificacion;

import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencias;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio de dominio con las reglas de clasificación de un usuario: una instantánea inmutable de su
 * catálogo ordenada por prioridad (docs/adr/0014), sin estado ni dependencias, y fácil de probar.
 *
 * <p>Las reglas, en lenguaje del negocio:
 * <ul>
 *   <li>Una etiqueta se aplica si alguna de sus ocurrencias aparece en el concepto; se aplican todas
 *   las que coincidan.</li>
 *   <li>La <b>cascada</b> da a una transacción sin categoría la categoría de la etiqueta de mayor
 *   prioridad (menor número) de entre las suyas que tenga una.</li>
 *   <li>Por ocurrencias, la categoría solo se asigna si no hay ninguna, y gana la de mayor prioridad.</li>
 * </ul>
 * A igual prioridad se desempata por nombre normalizado, para que el resultado sea siempre el mismo.
 */
public final class MotorClasificacion {

	private record ReglaCategoria(CategoriaId id, int prioridad, String nombre, Ocurrencias ocurrencias) {
	}

	private record ReglaEtiqueta(EtiquetaId id, int prioridad, String nombre, Ocurrencias ocurrencias, CategoriaId cascada) {
	}

	private final List<ReglaCategoria> categorias;

	private final List<ReglaEtiqueta> etiquetas;

	private final Map<EtiquetaId, ReglaEtiqueta> etiquetasPorId;

	private MotorClasificacion(List<ReglaCategoria> categorias, List<ReglaEtiqueta> etiquetas) {
		this.categorias = categorias;
		this.etiquetas = etiquetas;
		this.etiquetasPorId = etiquetas.stream().collect(Collectors.toMap(ReglaEtiqueta::id, Function.identity()));
	}

	public static MotorClasificacion de(List<Categoria> categorias, List<Etiqueta> etiquetas) {
		return new MotorClasificacion(
				categorias.stream()
						.map(c -> new ReglaCategoria(c.id(), c.prioridad(), c.nombre().normalizado(), c.ocurrencias()))
						.sorted(Comparator.comparingInt(ReglaCategoria::prioridad).thenComparing(ReglaCategoria::nombre))
						.toList(),
				etiquetas.stream()
						.map(e -> new ReglaEtiqueta(e.id(), e.prioridad(), e.nombre().normalizado(), e.ocurrencias(), e.categoriaId()))
						.sorted(Comparator.comparingInt(ReglaEtiqueta::prioridad).thenComparing(ReglaEtiqueta::nombre))
						.toList());
	}

	/** Etiquetas cuyas ocurrencias aparecen en el concepto, en orden de prioridad. */
	public List<EtiquetaId> etiquetasQueCasan(String conceptoNormalizado) {
		return this.etiquetas.stream()
				.filter(regla -> regla.ocurrencias().coincideCon(conceptoNormalizado))
				.map(ReglaEtiqueta::id)
				.toList();
	}

	/** Categoría en cascada: la de la etiqueta asignada de mayor prioridad que tenga una. */
	public Optional<CategoriaId> cascada(Collection<EtiquetaId> asignadas) {
		return asignadas.stream()
				.map(this.etiquetasPorId::get)
				.filter(regla -> regla != null && regla.cascada() != null)
				.min(Comparator.comparingInt(ReglaEtiqueta::prioridad).thenComparing(ReglaEtiqueta::nombre))
				.map(ReglaEtiqueta::cascada);
	}

	/** Primera categoría (por prioridad) con alguna ocurrencia en el concepto. */
	public Optional<CategoriaId> categoriaQueCasa(String conceptoNormalizado) {
		return this.categorias.stream()
				.filter(regla -> regla.ocurrencias().coincideCon(conceptoNormalizado))
				.map(ReglaCategoria::id)
				.findFirst();
	}
}
