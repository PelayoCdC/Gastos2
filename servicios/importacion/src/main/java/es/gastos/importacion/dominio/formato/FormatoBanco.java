package es.gastos.importacion.dominio.formato;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.Objects;

/** Cómo leer los extractos de un banco. */
public final class FormatoBanco extends RaizAgregado<FormatoId> {

	private final FormatoId id;

	private final UsuarioId propietario;

	private NombreFormato nombre;

	private int filaInicio;

	private MapeoColumnas columnas;

	private FormatoFecha formatoFecha;

	private FormatoBanco(FormatoId id, UsuarioId propietario, NombreFormato nombre, int filaInicio, MapeoColumnas columnas,
			FormatoFecha formatoFecha, long version) {
		super(version);
		this.id = Objects.requireNonNull(id);
		this.propietario = Objects.requireNonNull(propietario);
		this.nombre = Objects.requireNonNull(nombre);
		this.filaInicio = filaInicio(filaInicio);
		this.columnas = Objects.requireNonNull(columnas);
		this.formatoFecha = Objects.requireNonNull(formatoFecha);
	}

	public static FormatoBanco crear(FormatoId id, UsuarioId propietario, NombreFormato nombre, int filaInicio,
			MapeoColumnas columnas, FormatoFecha formatoFecha) {
		FormatoBanco formato = new FormatoBanco(id, propietario, nombre, filaInicio, columnas, formatoFecha, 0);
		formato.anunciar();
		return formato;
	}

	public static FormatoBanco reconstituir(FormatoId id, UsuarioId propietario, NombreFormato nombre, int filaInicio,
			MapeoColumnas columnas, FormatoFecha formatoFecha, long version) {
		return new FormatoBanco(id, propietario, nombre, filaInicio, columnas, formatoFecha, version);
	}

	public void modificar(NombreFormato nuevoNombre, int nuevaFilaInicio, MapeoColumnas nuevasColumnas, FormatoFecha nuevoFormato) {
		if (this.nombre.equals(nuevoNombre) && this.filaInicio == nuevaFilaInicio && this.columnas.equals(nuevasColumnas)
				&& this.formatoFecha.equals(nuevoFormato)) {
			return;
		}
		this.nombre = nuevoNombre;
		this.filaInicio = filaInicio(nuevaFilaInicio);
		this.columnas = nuevasColumnas;
		this.formatoFecha = nuevoFormato;
		this.anunciar();
	}

	public void eliminar() {
		this.registrarEvento(new FormatoBancoEliminado(this.id, this.propietario, this.siguienteVersion()));
	}

	private void anunciar() {
		this.registrarEvento(new FormatoBancoGuardado(this.id, this.propietario, this.siguienteVersion(), this.nombre,
				this.filaInicio, this.columnas, this.formatoFecha));
	}

	/** La fila de inicio cuenta desde 1, como en la hoja de cálculo que ve el usuario. */
	private static int filaInicio(int fila) {
		if (fila < 1) {
			throw new ExcepcionDominio("FILA_INICIO_INVALIDA", "La fila de inicio empieza en 1");
		}
		return fila;
	}

	@Override
	public FormatoId id() {
		return this.id;
	}

	@Override
	public UsuarioId propietario() {
		return this.propietario;
	}

	public NombreFormato nombre() {
		return this.nombre;
	}

	public int filaInicio() {
		return this.filaInicio;
	}

	public MapeoColumnas columnas() {
		return this.columnas;
	}

	public FormatoFecha formatoFecha() {
		return this.formatoFecha;
	}
}
