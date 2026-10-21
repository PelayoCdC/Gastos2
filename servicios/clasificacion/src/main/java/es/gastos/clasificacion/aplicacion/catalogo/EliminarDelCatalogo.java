package es.gastos.clasificacion.aplicacion.catalogo;

import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionRepositorio;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.UsuarioId;

/**
 * Borrado de categorías y etiquetas con todo lo que arrastran, en una sola transacción local
 * (posible porque las asignaciones viven en este mismo contexto, docs/adr/0002):
 * <ul>
 *   <li>Categoría: se quita de todas las transacciones (por conjuntos) y de las etiquetas que la
 *   aplicaban en cascada, que siguen existiendo sin cascada. En el back antiguo esto daba un 500.</li>
 *   <li>Etiqueta: se quita de todas las transacciones (por conjuntos). Las categorías que dejó por
 *   cascada se conservan: ya son de la transacción.</li>
 * </ul>
 */
@CasoDeUso
public class EliminarDelCatalogo {

	private final CatalogoRepositorio catalogo;

	private final ClasificacionRepositorio clasificaciones;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public EliminarDelCatalogo(CatalogoRepositorio catalogo, ClasificacionRepositorio clasificaciones, PublicadorEventos publicador,
			UnidadDeTrabajo unidadDeTrabajo) {
		this.catalogo = catalogo;
		this.clasificaciones = clasificaciones;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public void eliminarCategoria(UsuarioId usuario, CategoriaId id) {
		this.unidadDeTrabajo.ejecutar(() -> {
			Categoria categoria = Propiedad.exigir(this.catalogo.buscarCategoria(id), usuario, "la categoria", id);

			for (Etiqueta etiqueta : this.catalogo.etiquetasDeCategoria(id)) {
				etiqueta.quitarCategoria();
				this.catalogo.guardar(etiqueta);
				this.publicador.publicarDe(etiqueta);
			}
			this.clasificaciones.desasignarCategoria(id);

			categoria.eliminar();
			this.catalogo.eliminar(categoria);
			this.publicador.publicarDe(categoria);
		});
	}

	public void eliminarEtiqueta(UsuarioId usuario, EtiquetaId id) {
		this.unidadDeTrabajo.ejecutar(() -> {
			Etiqueta etiqueta = Propiedad.exigir(this.catalogo.buscarEtiqueta(id), usuario, "la etiqueta", id);
			this.clasificaciones.quitarEtiqueta(id);
			etiqueta.eliminar();
			this.catalogo.eliminar(etiqueta);
			this.publicador.publicarDe(etiqueta);
		});
	}
}
