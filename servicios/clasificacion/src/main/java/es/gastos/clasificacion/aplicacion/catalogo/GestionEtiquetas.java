package es.gastos.clasificacion.aplicacion.catalogo;

import es.gastos.clasificacion.aplicacion.catalogo.VistasCatalogo.DatosEtiqueta;
import es.gastos.clasificacion.aplicacion.siembra.SembrarCatalogoInicial;
import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.catalogo.NombreCatalogo;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencias;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Alta, modificación y consulta de etiquetas. La categoría en cascada, si se indica, tiene que ser
 * del mismo usuario (si no, 404: para él no existe). En el back antiguo se ignoraba en silencio.
 */
@CasoDeUso
public class GestionEtiquetas {

	public record Datos(String nombre, String descripcion, List<String> ocurrencias, UUID categoriaId) {
	}

	/** Filtro del listado: por categoría en cascada y/o las que no tienen ninguna (combinados con OR). */
	public record Filtro(List<UUID> categorias, boolean sinCategoria) {

		public static final Filtro TODAS = new Filtro(List.of(), false);

		boolean incluye(Etiqueta etiqueta) {
			if (this.categorias.isEmpty() && !this.sinCategoria) {
				return true;
			}
			return (this.sinCategoria && etiqueta.categoriaId() == null)
					|| (etiqueta.categoriaId() != null && this.categorias.contains(etiqueta.categoriaId().valor()));
		}
	}

	private final CatalogoRepositorio catalogo;

	private final SembrarCatalogoInicial siembra;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	public GestionEtiquetas(CatalogoRepositorio catalogo, SembrarCatalogoInicial siembra, PublicadorEventos publicador,
			UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids) {
		this.catalogo = catalogo;
		this.siembra = siembra;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
	}

	public DatosEtiqueta crear(UsuarioId usuario, Datos datos) {
		NombreCatalogo nombre = new NombreCatalogo(datos.nombre());
		Ocurrencias ocurrencias = Ocurrencias.de(datos.ocurrencias());

		return this.unidadDeTrabajo.ejecutar(() -> {
			this.siembra.asegurar(usuario);
			this.exigirNombreLibre(usuario, nombre, null);
			CategoriaId cascada = this.categoriaPropia(usuario, datos.categoriaId());
			Etiqueta etiqueta = Etiqueta.crear(new EtiquetaId(this.ids.nuevo()), usuario, nombre, datos.descripcion(), ocurrencias,
					cascada, this.catalogo.siguientePrioridadEtiqueta(usuario));
			this.catalogo.guardar(etiqueta);
			this.publicador.publicarDe(etiqueta);
			return this.vista(usuario, etiqueta);
		});
	}

	public DatosEtiqueta modificar(UsuarioId usuario, EtiquetaId id, Datos datos) {
		NombreCatalogo nombre = new NombreCatalogo(datos.nombre());
		Ocurrencias ocurrencias = Ocurrencias.de(datos.ocurrencias());

		return this.unidadDeTrabajo.ejecutar(() -> {
			Etiqueta etiqueta = Propiedad.exigir(this.catalogo.buscarEtiqueta(id), usuario, "la etiqueta", id);
			this.exigirNombreLibre(usuario, nombre, id);
			etiqueta.modificar(nombre, datos.descripcion(), ocurrencias, this.categoriaPropia(usuario, datos.categoriaId()));
			this.catalogo.guardar(etiqueta);
			this.publicador.publicarDe(etiqueta);
			return this.vista(usuario, etiqueta);
		});
	}

	public List<DatosEtiqueta> listar(UsuarioId usuario, Filtro filtro) {
		this.siembra.asegurar(usuario);
		return this.unidadDeTrabajo.ejecutarLectura(() -> {
			Map<CategoriaId, Categoria> categorias = this.categoriasPorId(usuario);
			return this.catalogo.etiquetas(usuario).stream()
					.filter(filtro::incluye)
					.map(etiqueta -> DatosEtiqueta.de(etiqueta, categorias))
					.toList();
		});
	}

	public DatosEtiqueta consultar(UsuarioId usuario, EtiquetaId id) {
		return this.unidadDeTrabajo.ejecutarLectura(() ->
				this.vista(usuario, Propiedad.exigir(this.catalogo.buscarEtiqueta(id), usuario, "la etiqueta", id)));
	}

	private DatosEtiqueta vista(UsuarioId usuario, Etiqueta etiqueta) {
		return DatosEtiqueta.de(etiqueta, this.categoriasPorId(usuario));
	}

	private Map<CategoriaId, Categoria> categoriasPorId(UsuarioId usuario) {
		return this.catalogo.categorias(usuario).stream().collect(Collectors.toMap(Categoria::id, Function.identity()));
	}

	private CategoriaId categoriaPropia(UsuarioId usuario, UUID categoriaId) {
		if (categoriaId == null) {
			return null;
		}
		CategoriaId id = new CategoriaId(categoriaId);
		Propiedad.exigir(this.catalogo.buscarCategoria(id), usuario, "la categoria", id);
		return id;
	}

	private void exigirNombreLibre(UsuarioId usuario, NombreCatalogo nombre, EtiquetaId excepto) {
		this.catalogo.etiquetaPorNombre(usuario, nombre.normalizado())
				.filter(existente -> !existente.id().equals(excepto))
				.ifPresent(existente -> {
					throw Conflicto.nombreDuplicado("una etiqueta", nombre.valor());
				});
	}
}
