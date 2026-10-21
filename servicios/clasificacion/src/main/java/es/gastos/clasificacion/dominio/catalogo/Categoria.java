package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.Objects;

/**
 * Categoría del catálogo: la clasificación principal de una transacción (como mucho una por
 * transacción). Se aplica automáticamente si alguna de sus ocurrencias aparece en el concepto y la
 * transacción no tiene ya categoría; entre varias candidatas gana la de menor {@code prioridad}.
 */
public final class Categoria extends RaizAgregado<CategoriaId> {

	public static final int LONGITUD_DESCRIPCION = 500;

	private final CategoriaId id;

	private final UsuarioId propietario;

	private NombreCatalogo nombre;

	private Color color;

	private String descripcion;

	private Ocurrencias ocurrencias;

	private int prioridad;

	private Categoria(CategoriaId id, UsuarioId propietario, NombreCatalogo nombre, Color color, String descripcion,
			Ocurrencias ocurrencias, int prioridad, long version) {
		super(version);
		this.id = Objects.requireNonNull(id);
		this.propietario = Objects.requireNonNull(propietario);
		this.nombre = Objects.requireNonNull(nombre);
		this.color = color;
		this.descripcion = descripcion(descripcion);
		this.ocurrencias = Objects.requireNonNull(ocurrencias);
		this.prioridad = prioridad(prioridad);
	}

	public static Categoria crear(CategoriaId id, UsuarioId propietario, NombreCatalogo nombre, Color color, String descripcion,
			Ocurrencias ocurrencias, int prioridad) {
		Categoria categoria = new Categoria(id, propietario, nombre, color, descripcion, ocurrencias, prioridad, 0);
		categoria.anunciar();
		return categoria;
	}

	public static Categoria reconstituir(CategoriaId id, UsuarioId propietario, NombreCatalogo nombre, Color color,
			String descripcion, Ocurrencias ocurrencias, int prioridad, long version) {
		return new Categoria(id, propietario, nombre, color, descripcion, ocurrencias, prioridad, version);
	}

	public void modificar(NombreCatalogo nuevoNombre, Color nuevoColor, String nuevaDescripcion, Ocurrencias nuevasOcurrencias) {
		String descripcionLimpia = descripcion(nuevaDescripcion);
		if (this.nombre.equals(nuevoNombre) && Objects.equals(this.color, nuevoColor)
				&& Objects.equals(this.descripcion, descripcionLimpia) && this.ocurrencias.equals(nuevasOcurrencias)) {
			return;
		}
		this.nombre = nuevoNombre;
		this.color = nuevoColor;
		this.descripcion = descripcionLimpia;
		this.ocurrencias = nuevasOcurrencias;
		this.anunciar();
	}

	public void cambiarPrioridad(int nuevaPrioridad) {
		if (this.prioridad == nuevaPrioridad) {
			return;
		}
		this.prioridad = prioridad(nuevaPrioridad);
		this.anunciar();
	}

	public void eliminar() {
		this.registrarEvento(new CategoriaEliminada(this.id, this.propietario, this.siguienteVersion()));
	}

	private void anunciar() {
		this.registrarEvento(new CategoriaGuardada(this.id, this.propietario, this.siguienteVersion(), this.nombre, this.color,
				this.descripcion, this.ocurrencias, this.prioridad));
	}

	static String descripcion(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		String limpia = texto.strip();
		if (limpia.length() > LONGITUD_DESCRIPCION) {
			throw new ExcepcionDominio("DESCRIPCION_INVALIDA", "La descripcion admite como mucho " + LONGITUD_DESCRIPCION + " caracteres");
		}
		return limpia;
	}

	static int prioridad(int prioridad) {
		if (prioridad < 0) {
			throw new ExcepcionDominio("PRIORIDAD_INVALIDA", "La prioridad no puede ser negativa");
		}
		return prioridad;
	}

	@Override
	public CategoriaId id() {
		return this.id;
	}

	@Override
	public UsuarioId propietario() {
		return this.propietario;
	}

	public NombreCatalogo nombre() {
		return this.nombre;
	}

	public Color color() {
		return this.color;
	}

	public String descripcion() {
		return this.descripcion;
	}

	public Ocurrencias ocurrencias() {
		return this.ocurrencias;
	}

	public int prioridad() {
		return this.prioridad;
	}
}
