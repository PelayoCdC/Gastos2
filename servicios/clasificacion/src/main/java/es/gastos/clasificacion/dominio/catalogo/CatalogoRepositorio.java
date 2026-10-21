package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia del catálogo de un usuario. Categorías y etiquetas son agregados distintos
 * pero se consultan casi siempre juntas (las reglas de clasificación necesitan el catálogo entero),
 * por eso comparten repositorio. Las listas vienen ordenadas por prioridad y, a igualdad, por nombre.
 */
public interface CatalogoRepositorio {

	void guardar(Categoria categoria);

	void guardar(Etiqueta etiqueta);

	Optional<Categoria> buscarCategoria(CategoriaId id);

	Optional<Etiqueta> buscarEtiqueta(EtiquetaId id);

	List<Categoria> categorias(UsuarioId usuario);

	List<Etiqueta> etiquetas(UsuarioId usuario);

	List<Etiqueta> etiquetasDeCategoria(CategoriaId categoria);

	Optional<Categoria> categoriaPorNombre(UsuarioId usuario, String nombreNormalizado);

	Optional<Etiqueta> etiquetaPorNombre(UsuarioId usuario, String nombreNormalizado);

	/** Prioridad para una entrada nueva: detrás de todas las existentes. */
	int siguientePrioridadCategoria(UsuarioId usuario);

	int siguientePrioridadEtiqueta(UsuarioId usuario);

	void eliminar(Categoria categoria);

	void eliminar(Etiqueta etiqueta);
}
