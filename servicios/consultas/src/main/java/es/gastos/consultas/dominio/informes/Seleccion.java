package es.gastos.consultas.dominio.informes;

import java.util.Set;
import java.util.UUID;

/**
 * Qué movimientos entran en un informe: los de alguna de las {@code categorias} o, si no se indican,
 * los que tienen alguna de las {@code etiquetas}; sin ninguna, todos. Si llegan las dos, mandan las
 * categorías (como en el back antiguo: la página Análisis selecciona una cosa u otra).
 */
public record Seleccion(Set<UUID> categorias, Set<UUID> etiquetas) {

	public static final Seleccion TODO = new Seleccion(Set.of(), Set.of());

	public Seleccion {
		categorias = categorias == null ? Set.of() : Set.copyOf(categorias);
		etiquetas = etiquetas == null ? Set.of() : Set.copyOf(etiquetas);
	}

	public boolean incluye(Apunte apunte) {
		if (!this.categorias.isEmpty()) {
			return apunte.categoria() != null && this.categorias.contains(apunte.categoria().id());
		}
		if (!this.etiquetas.isEmpty()) {
			return apunte.etiquetas().stream().anyMatch(e -> this.etiquetas.contains(e.id()));
		}
		return true;
	}
}
