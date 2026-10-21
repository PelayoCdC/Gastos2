package es.gastos.clasificacion.aplicacion.catalogo;

import es.gastos.clasificacion.aplicacion.siembra.SembrarCatalogoInicial;
import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Color;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.catalogo.NombreCatalogo;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencias;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Exporta e importa las definiciones del catálogo por nombre (sin ids), para compartir reglas entre
 * usuarios o guardar una copia. La importación es un upsert por nombre normalizado: actualiza las que
 * existen, crea las que no (al final de la prioridad) y nunca borra. La categoría en cascada de una
 * etiqueta se busca por nombre y, si no existe, se crea vacía.
 */
@CasoDeUso
public class DefinicionesCatalogo {

	public record Definiciones(List<DefinicionCategoria> categorias, List<DefinicionEtiqueta> etiquetas) {

		public Definiciones {
			categorias = categorias == null ? List.of() : List.copyOf(categorias);
			etiquetas = etiquetas == null ? List.of() : List.copyOf(etiquetas);
		}
	}

	public record DefinicionCategoria(String nombre, String color, String descripcion, List<String> ocurrencias) {
	}

	/** {@code categoria} es el nombre de la categoría en cascada, o nulo. */
	public record DefinicionEtiqueta(String nombre, String descripcion, List<String> ocurrencias, String categoria) {
	}

	public record Resultado(Recuento categorias, Recuento etiquetas) {
	}

	public record Recuento(int creadas, int actualizadas) {
	}

	private final CatalogoRepositorio catalogo;

	private final SembrarCatalogoInicial siembra;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	public DefinicionesCatalogo(CatalogoRepositorio catalogo, SembrarCatalogoInicial siembra, PublicadorEventos publicador,
			UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids) {
		this.catalogo = catalogo;
		this.siembra = siembra;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
	}

	public Definiciones exportar(UsuarioId usuario) {
		this.siembra.asegurar(usuario);
		return this.unidadDeTrabajo.ejecutarLectura(() -> {
			List<Categoria> categorias = this.catalogo.categorias(usuario);
			Map<CategoriaId, String> nombres = categorias.stream()
					.collect(Collectors.toMap(Categoria::id, c -> c.nombre().valor()));
			return new Definiciones(
					categorias.stream().map(c -> new DefinicionCategoria(c.nombre().valor(),
							c.color() == null ? null : c.color().valor(), c.descripcion(), c.ocurrencias().textos())).toList(),
					this.catalogo.etiquetas(usuario).stream().map(e -> new DefinicionEtiqueta(e.nombre().valor(), e.descripcion(),
							e.ocurrencias().textos(), e.categoriaId() == null ? null : nombres.get(e.categoriaId()))).toList());
		});
	}

	public Resultado importar(UsuarioId usuario, Definiciones definiciones) {
		return this.unidadDeTrabajo.ejecutar(() -> {
			this.siembra.asegurar(usuario);
			int[] categorias = {0, 0};
			int[] etiquetas = {0, 0};

			for (DefinicionCategoria definicion : definiciones.categorias()) {
				if (definicion.nombre() == null || definicion.nombre().isBlank()) {
					continue;
				}
				NombreCatalogo nombre = new NombreCatalogo(definicion.nombre());
				Optional<Categoria> existente = this.catalogo.categoriaPorNombre(usuario, nombre.normalizado());
				Categoria categoria = existente.orElseGet(() -> Categoria.crear(new CategoriaId(this.ids.nuevo()), usuario, nombre,
						Color.opcional(definicion.color()), definicion.descripcion(), Ocurrencias.de(definicion.ocurrencias()),
						this.catalogo.siguientePrioridadCategoria(usuario)));
				if (existente.isPresent()) {
					// Se conserva el nombre tal y como lo tiene el usuario: el fichero solo lo usa para emparejar
					categoria.modificar(categoria.nombre(), Color.opcional(definicion.color()), definicion.descripcion(),
							Ocurrencias.de(definicion.ocurrencias()));
				}
				categorias[existente.isPresent() ? 1 : 0]++;
				this.catalogo.guardar(categoria);
				this.publicador.publicarDe(categoria);
			}

			for (DefinicionEtiqueta definicion : definiciones.etiquetas()) {
				if (definicion.nombre() == null || definicion.nombre().isBlank()) {
					continue;
				}
				NombreCatalogo nombre = new NombreCatalogo(definicion.nombre());
				CategoriaId cascada = this.categoriaPorNombre(usuario, definicion.categoria(), categorias);
				Optional<Etiqueta> existente = this.catalogo.etiquetaPorNombre(usuario, nombre.normalizado());
				Etiqueta etiqueta = existente.orElseGet(() -> Etiqueta.crear(new EtiquetaId(this.ids.nuevo()), usuario, nombre,
						definicion.descripcion(), Ocurrencias.de(definicion.ocurrencias()), cascada,
						this.catalogo.siguientePrioridadEtiqueta(usuario)));
				if (existente.isPresent()) {
					etiqueta.modificar(etiqueta.nombre(), definicion.descripcion(), Ocurrencias.de(definicion.ocurrencias()), cascada);
				}
				etiquetas[existente.isPresent() ? 1 : 0]++;
				this.catalogo.guardar(etiqueta);
				this.publicador.publicarDe(etiqueta);
			}

			return new Resultado(new Recuento(categorias[0], categorias[1]), new Recuento(etiquetas[0], etiquetas[1]));
		});
	}

	/** Busca la categoría por nombre o la crea vacía (contándola como creada). */
	private CategoriaId categoriaPorNombre(UsuarioId usuario, String nombreTexto, int[] recuentoCategorias) {
		if (nombreTexto == null || nombreTexto.isBlank()) {
			return null;
		}
		NombreCatalogo nombre = new NombreCatalogo(nombreTexto);
		return this.catalogo.categoriaPorNombre(usuario, nombre.normalizado())
				.map(Categoria::id)
				.orElseGet(() -> {
					Categoria nueva = Categoria.crear(new CategoriaId(this.ids.nuevo()), usuario, nombre, null, null,
							Ocurrencias.NINGUNA, this.catalogo.siguientePrioridadCategoria(usuario));
					this.catalogo.guardar(nueva);
					this.publicador.publicarDe(nueva);
					recuentoCategorias[0]++;
					return nueva.id();
				});
	}
}
