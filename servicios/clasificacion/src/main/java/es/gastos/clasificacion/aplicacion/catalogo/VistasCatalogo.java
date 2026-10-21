package es.gastos.clasificacion.aplicacion.catalogo;

import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Representaciones de lectura del catálogo que devuelven los casos de uso. */
public final class VistasCatalogo {

	private VistasCatalogo() {
	}

	public record DatosCategoria(UUID id, String nombre, String color, String descripcion, List<String> ocurrencias,
			int prioridad) {

		static DatosCategoria de(Categoria categoria) {
			return new DatosCategoria(categoria.id().valor(), categoria.nombre().valor(),
					categoria.color() == null ? null : categoria.color().valor(), categoria.descripcion(),
					categoria.ocurrencias().textos(), categoria.prioridad());
		}
	}

	/** {@code categoria} es la que aplica en cascada (o nula), con lo justo para mostrarla. */
	public record DatosEtiqueta(UUID id, String nombre, String descripcion, List<String> ocurrencias,
			CategoriaResumida categoria, int prioridad) {

		static DatosEtiqueta de(Etiqueta etiqueta, Map<CategoriaId, Categoria> categorias) {
			Categoria cascada = etiqueta.categoriaId() == null ? null : categorias.get(etiqueta.categoriaId());
			return new DatosEtiqueta(etiqueta.id().valor(), etiqueta.nombre().valor(), etiqueta.descripcion(),
					etiqueta.ocurrencias().textos(),
					cascada == null ? null : new CategoriaResumida(cascada.id().valor(), cascada.nombre().valor(),
							cascada.color() == null ? null : cascada.color().valor()),
					etiqueta.prioridad());
		}
	}

	public record CategoriaResumida(UUID id, String nombre, String color) {
	}
}
