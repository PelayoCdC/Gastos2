package es.gastos.clasificacion.aplicacion.catalogo;

import es.gastos.clasificacion.aplicacion.catalogo.VistasCatalogo.DatosCategoria;
import es.gastos.clasificacion.aplicacion.siembra.SembrarCatalogoInicial;
import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Color;
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

/** Alta, modificación y consulta de categorías (nombre único por usuario; las nuevas van al final). */
@CasoDeUso
public class GestionCategorias {

	public record Datos(String nombre, String color, String descripcion, List<String> ocurrencias) {
	}

	private final CatalogoRepositorio catalogo;

	private final SembrarCatalogoInicial siembra;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	public GestionCategorias(CatalogoRepositorio catalogo, SembrarCatalogoInicial siembra, PublicadorEventos publicador,
			UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids) {
		this.catalogo = catalogo;
		this.siembra = siembra;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
	}

	public DatosCategoria crear(UsuarioId usuario, Datos datos) {
		NombreCatalogo nombre = new NombreCatalogo(datos.nombre());
		Color color = Color.opcional(datos.color());
		Ocurrencias ocurrencias = Ocurrencias.de(datos.ocurrencias());

		return this.unidadDeTrabajo.ejecutar(() -> {
			this.siembra.asegurar(usuario);
			this.exigirNombreLibre(usuario, nombre, null);
			Categoria categoria = Categoria.crear(new CategoriaId(this.ids.nuevo()), usuario, nombre, color, datos.descripcion(),
					ocurrencias, this.catalogo.siguientePrioridadCategoria(usuario));
			this.catalogo.guardar(categoria);
			this.publicador.publicarDe(categoria);
			return DatosCategoria.de(categoria);
		});
	}

	public DatosCategoria modificar(UsuarioId usuario, CategoriaId id, Datos datos) {
		NombreCatalogo nombre = new NombreCatalogo(datos.nombre());
		Color color = Color.opcional(datos.color());
		Ocurrencias ocurrencias = Ocurrencias.de(datos.ocurrencias());

		return this.unidadDeTrabajo.ejecutar(() -> {
			Categoria categoria = Propiedad.exigir(this.catalogo.buscarCategoria(id), usuario, "la categoria", id);
			this.exigirNombreLibre(usuario, nombre, id);
			categoria.modificar(nombre, color, datos.descripcion(), ocurrencias);
			this.catalogo.guardar(categoria);
			this.publicador.publicarDe(categoria);
			return DatosCategoria.de(categoria);
		});
	}

	public List<DatosCategoria> listar(UsuarioId usuario) {
		this.siembra.asegurar(usuario);
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.catalogo.categorias(usuario).stream().map(DatosCategoria::de).toList());
	}

	public DatosCategoria consultar(UsuarioId usuario, CategoriaId id) {
		return this.unidadDeTrabajo.ejecutarLectura(() ->
				DatosCategoria.de(Propiedad.exigir(this.catalogo.buscarCategoria(id), usuario, "la categoria", id)));
	}

	private void exigirNombreLibre(UsuarioId usuario, NombreCatalogo nombre, CategoriaId excepto) {
		this.catalogo.categoriaPorNombre(usuario, nombre.normalizado())
				.filter(existente -> !existente.id().equals(excepto))
				.ifPresent(existente -> {
					throw Conflicto.nombreDuplicado("una categoria", nombre.valor());
				});
	}
}
