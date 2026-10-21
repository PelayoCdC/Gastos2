package es.gastos.clasificacion.aplicacion.clasificacion;

import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.catalogo.NombreCatalogo;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencias;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Traduce lo que pide el cliente a entradas del catálogo: ids existentes (que deben ser del usuario)
 * y nombres nuevos, que se buscan por su forma normalizada y se crean si no existen (al final de la
 * prioridad). Así se puede clasificar con una categoría que todavía no existe en un solo paso.
 */
final class ResolucionCatalogo {

	private final CatalogoRepositorio catalogo;

	private final PublicadorEventos publicador;

	private final GeneradorIds ids;

	ResolucionCatalogo(CatalogoRepositorio catalogo, PublicadorEventos publicador, GeneradorIds ids) {
		this.catalogo = catalogo;
		this.publicador = publicador;
		this.ids = ids;
	}

	/** {@code id} tiene preferencia; si no hay ni id ni nombre, no hay categoría. */
	CategoriaId categoria(UsuarioId usuario, UUID id, String nombreNuevo) {
		if (id != null) {
			CategoriaId categoria = new CategoriaId(id);
			Propiedad.exigir(this.catalogo.buscarCategoria(categoria), usuario, "la categoria", categoria);
			return categoria;
		}
		if (nombreNuevo == null || nombreNuevo.isBlank()) {
			return null;
		}
		NombreCatalogo nombre = new NombreCatalogo(nombreNuevo);
		return this.catalogo.categoriaPorNombre(usuario, nombre.normalizado()).map(Categoria::id).orElseGet(() -> {
			Categoria nueva = Categoria.crear(new CategoriaId(this.ids.nuevo()), usuario, nombre, null, null, Ocurrencias.NINGUNA,
					this.catalogo.siguientePrioridadCategoria(usuario));
			this.catalogo.guardar(nueva);
			this.publicador.publicarDe(nueva);
			return nueva.id();
		});
	}

	/** Etiquetas por id (del usuario) más las nuevas por nombre, sin repetir y en el orden pedido. */
	List<EtiquetaId> etiquetas(UsuarioId usuario, List<UUID> existentes, List<String> nombresNuevos) {
		Set<EtiquetaId> resultado = new LinkedHashSet<>();
		for (UUID id : existentes == null ? List.<UUID>of() : existentes) {
			EtiquetaId etiqueta = new EtiquetaId(id);
			Propiedad.exigir(this.catalogo.buscarEtiqueta(etiqueta), usuario, "la etiqueta", etiqueta);
			resultado.add(etiqueta);
		}
		for (String nombreNuevo : nombresNuevos == null ? List.<String>of() : nombresNuevos) {
			if (nombreNuevo == null || nombreNuevo.isBlank()) {
				continue;
			}
			NombreCatalogo nombre = new NombreCatalogo(nombreNuevo);
			resultado.add(this.catalogo.etiquetaPorNombre(usuario, nombre.normalizado()).map(Etiqueta::id).orElseGet(() -> {
				Etiqueta nueva = Etiqueta.crear(new EtiquetaId(this.ids.nuevo()), usuario, nombre, null, Ocurrencias.NINGUNA, null,
						this.catalogo.siguientePrioridadEtiqueta(usuario));
				this.catalogo.guardar(nueva);
				this.publicador.publicarDe(nueva);
				return nueva.id();
			}));
		}
		return new ArrayList<>(resultado);
	}
}
