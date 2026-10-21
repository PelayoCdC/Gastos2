package es.gastos.clasificacion.aplicacion.siembra;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Siembra el catálogo inicial de un usuario la primera vez que hace falta.
 *
 * <p>Se dispara al llegar {@code UsuarioRegistrado} y, por si el evento aún no ha llegado, al
 * principio de cualquier caso de uso de clasificación ("siembra perezosa"). Así un usuario recién
 * registrado nunca ve un catálogo vacío ni puede crear una categoría que luego choque con las
 * iniciales. Es idempotente: solo siembra quien consigue la marca de {@link RegistroSiembra}.
 */
@CasoDeUso
public class SembrarCatalogoInicial {

	static final String DESCRIPCION_CATEGORIA = "Categoria creada por defecto";

	static final String DESCRIPCION_ETIQUETA = "Etiqueta creada por defecto";

	private final RegistroSiembra registro;

	private final FuenteCatalogoInicial fuente;

	private final CatalogoRepositorio catalogo;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	public SembrarCatalogoInicial(RegistroSiembra registro, FuenteCatalogoInicial fuente, CatalogoRepositorio catalogo,
			PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids) {
		this.registro = registro;
		this.fuente = fuente;
		this.catalogo = catalogo;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
	}

	/** Siembra si nunca se sembró; devuelve si lo ha hecho ahora. Se une a la transacción en curso si la hay. */
	public boolean asegurar(UsuarioId usuario) {
		return this.unidadDeTrabajo.ejecutar(() -> {
			if (!this.registro.marcar(usuario)) {
				return false;
			}

			Map<String, CategoriaId> categoriasPorNombre = new HashMap<>();
			List<FuenteCatalogoInicial.CategoriaInicial> categorias = this.fuente.categorias();
			for (int i = 0; i < categorias.size(); i++) {
				FuenteCatalogoInicial.CategoriaInicial inicial = categorias.get(i);
				Categoria categoria = Categoria.crear(new CategoriaId(this.ids.nuevo()), usuario, new NombreCatalogo(inicial.nombre()),
						Color.opcional(inicial.color()), DESCRIPCION_CATEGORIA, Ocurrencias.de(inicial.ocurrencias()), i + 1);
				this.catalogo.guardar(categoria);
				this.publicador.publicarDe(categoria);
				categoriasPorNombre.put(inicial.nombre(), categoria.id());
			}

			List<FuenteCatalogoInicial.EtiquetaInicial> etiquetas = this.fuente.etiquetas();
			for (int i = 0; i < etiquetas.size(); i++) {
				FuenteCatalogoInicial.EtiquetaInicial inicial = etiquetas.get(i);
				CategoriaId cascada = inicial.categoria() == null ? null : categoriasPorNombre.get(inicial.categoria());
				Etiqueta etiqueta = Etiqueta.crear(new EtiquetaId(this.ids.nuevo()), usuario, new NombreCatalogo(inicial.nombre()),
						DESCRIPCION_ETIQUETA, Ocurrencias.de(inicial.ocurrencias()), cascada, i + 1);
				this.catalogo.guardar(etiqueta);
				this.publicador.publicarDe(etiqueta);
			}
			return true;
		});
	}
}
