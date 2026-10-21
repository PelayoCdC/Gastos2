package es.gastos.clasificacion.aplicacion.catalogo;

import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cambia el orden de prioridad de las categorías o de las etiquetas (docs/adr/0014). La petición
 * debe ser una permutación completa de las del usuario: así el orden resultante es exactamente el
 * enviado, sin huecos ni ambigüedades. Solo las que cambian de puesto emiten evento.
 */
@CasoDeUso
public class ReordenarCatalogo {

	private final CatalogoRepositorio catalogo;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public ReordenarCatalogo(CatalogoRepositorio catalogo, PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo) {
		this.catalogo = catalogo;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public void categorias(UsuarioId usuario, List<UUID> orden) {
		this.unidadDeTrabajo.ejecutar(() -> {
			Map<UUID, Categoria> categorias = this.catalogo.categorias(usuario).stream()
					.collect(Collectors.toMap(c -> c.id().valor(), Function.identity()));
			exigirPermutacion(orden, categorias.keySet());
			for (int i = 0; i < orden.size(); i++) {
				Categoria categoria = categorias.get(orden.get(i));
				categoria.cambiarPrioridad(i + 1);
				this.catalogo.guardar(categoria);
				this.publicador.publicarDe(categoria);
			}
		});
	}

	public void etiquetas(UsuarioId usuario, List<UUID> orden) {
		this.unidadDeTrabajo.ejecutar(() -> {
			Map<UUID, Etiqueta> etiquetas = this.catalogo.etiquetas(usuario).stream()
					.collect(Collectors.toMap(e -> e.id().valor(), Function.identity()));
			exigirPermutacion(orden, etiquetas.keySet());
			for (int i = 0; i < orden.size(); i++) {
				Etiqueta etiqueta = etiquetas.get(orden.get(i));
				etiqueta.cambiarPrioridad(i + 1);
				this.catalogo.guardar(etiqueta);
				this.publicador.publicarDe(etiqueta);
			}
		});
	}

	private static void exigirPermutacion(List<UUID> orden, java.util.Set<UUID> existentes) {
		if (orden == null || orden.size() != existentes.size() || !new HashSet<>(orden).equals(existentes)) {
			throw new ExcepcionDominio("ORDEN_INVALIDO", "El orden debe incluir exactamente una vez cada una de tus entradas");
		}
	}
}
