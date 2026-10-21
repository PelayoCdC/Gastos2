package es.gastos.clasificacion.aplicacion.siembra;

import java.util.List;

/**
 * Puerto de salida: el catálogo con el que empieza cada usuario. El orden de las listas es la
 * prioridad inicial (docs/adr/0014), así que el adaptador debe conservarlo.
 */
public interface FuenteCatalogoInicial {

	List<CategoriaInicial> categorias();

	List<EtiquetaInicial> etiquetas();

	record CategoriaInicial(String nombre, String color, List<String> ocurrencias) {
	}

	/** {@code categoria} es el nombre de una de las categorías iniciales, o nulo. */
	record EtiquetaInicial(String nombre, String categoria, List<String> ocurrencias) {
	}
}
