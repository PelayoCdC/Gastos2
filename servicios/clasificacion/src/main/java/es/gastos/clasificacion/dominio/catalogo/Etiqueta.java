package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.Objects;

/**
 * Etiqueta del catálogo: una transacción puede tener varias. Se aplican automáticamente todas las
 * que tengan alguna ocurrencia en el concepto. Si lleva {@code categoriaId}, aplica esa categoría en
 * <b>cascada</b> a las transacciones sin categoría (la de la etiqueta de menor prioridad gana).
 */
public final class Etiqueta extends RaizAgregado<EtiquetaId> {

	private final EtiquetaId id;

	private final UsuarioId propietario;

	private NombreCatalogo nombre;

	private String descripcion;

	private Ocurrencias ocurrencias;

	private CategoriaId categoriaId;

	private int prioridad;

	private Etiqueta(EtiquetaId id, UsuarioId propietario, NombreCatalogo nombre, String descripcion, Ocurrencias ocurrencias,
			CategoriaId categoriaId, int prioridad, long version) {
		super(version);
		this.id = Objects.requireNonNull(id);
		this.propietario = Objects.requireNonNull(propietario);
		this.nombre = Objects.requireNonNull(nombre);
		this.descripcion = Categoria.descripcion(descripcion);
		this.ocurrencias = Objects.requireNonNull(ocurrencias);
		this.categoriaId = categoriaId;
		this.prioridad = Categoria.prioridad(prioridad);
	}

	/** La categoría en cascada, si se indica, debe ser del mismo usuario: lo comprueba el caso de uso. */
	public static Etiqueta crear(EtiquetaId id, UsuarioId propietario, NombreCatalogo nombre, String descripcion,
			Ocurrencias ocurrencias, CategoriaId categoriaId, int prioridad) {
		Etiqueta etiqueta = new Etiqueta(id, propietario, nombre, descripcion, ocurrencias, categoriaId, prioridad, 0);
		etiqueta.anunciar();
		return etiqueta;
	}

	public static Etiqueta reconstituir(EtiquetaId id, UsuarioId propietario, NombreCatalogo nombre, String descripcion,
			Ocurrencias ocurrencias, CategoriaId categoriaId, int prioridad, long version) {
		return new Etiqueta(id, propietario, nombre, descripcion, ocurrencias, categoriaId, prioridad, version);
	}

	public void modificar(NombreCatalogo nuevoNombre, String nuevaDescripcion, Ocurrencias nuevasOcurrencias,
			CategoriaId nuevaCategoria) {
		String descripcionLimpia = Categoria.descripcion(nuevaDescripcion);
		if (this.nombre.equals(nuevoNombre) && Objects.equals(this.descripcion, descripcionLimpia)
				&& this.ocurrencias.equals(nuevasOcurrencias) && Objects.equals(this.categoriaId, nuevaCategoria)) {
			return;
		}
		this.nombre = nuevoNombre;
		this.descripcion = descripcionLimpia;
		this.ocurrencias = nuevasOcurrencias;
		this.categoriaId = nuevaCategoria;
		this.anunciar();
	}

	/** Su categoría en cascada se ha borrado: la etiqueta sigue existiendo, sin cascada. */
	public void quitarCategoria() {
		if (this.categoriaId == null) {
			return;
		}
		this.categoriaId = null;
		this.anunciar();
	}

	public void cambiarPrioridad(int nuevaPrioridad) {
		if (this.prioridad == nuevaPrioridad) {
			return;
		}
		this.prioridad = Categoria.prioridad(nuevaPrioridad);
		this.anunciar();
	}

	public void eliminar() {
		this.registrarEvento(new EtiquetaEliminada(this.id, this.propietario, this.siguienteVersion()));
	}

	private void anunciar() {
		this.registrarEvento(new EtiquetaGuardada(this.id, this.propietario, this.siguienteVersion(), this.nombre,
				this.descripcion, this.ocurrencias, this.categoriaId, this.prioridad));
	}

	@Override
	public EtiquetaId id() {
		return this.id;
	}

	@Override
	public UsuarioId propietario() {
		return this.propietario;
	}

	public NombreCatalogo nombre() {
		return this.nombre;
	}

	public String descripcion() {
		return this.descripcion;
	}

	public Ocurrencias ocurrencias() {
		return this.ocurrencias;
	}

	public CategoriaId categoriaId() {
		return this.categoriaId;
	}

	public int prioridad() {
		return this.prioridad;
	}
}
