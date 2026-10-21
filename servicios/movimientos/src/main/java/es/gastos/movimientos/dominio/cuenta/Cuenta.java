package es.gastos.movimientos.dominio.cuenta;

import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.comun.Textos;

import java.util.Objects;

/** Cuenta bancaria del usuario. */
public final class Cuenta extends RaizAgregado<CuentaId> {

	public static final int LONGITUD_BANCO = 100;

	public static final int LONGITUD_DESCRIPCION = 500;

	private final CuentaId id;

	private final UsuarioId propietario;

	private NombreCuenta nombre;

	private String banco;

	private String descripcion;

	private Cuenta(CuentaId id, UsuarioId propietario, NombreCuenta nombre, String banco, String descripcion, long version) {
		super(version);
		this.id = Objects.requireNonNull(id);
		this.propietario = Objects.requireNonNull(propietario);
		this.nombre = Objects.requireNonNull(nombre);
		this.banco = Textos.opcional(banco, LONGITUD_BANCO, "BANCO_INVALIDO", "El banco");
		this.descripcion = Textos.opcional(descripcion, LONGITUD_DESCRIPCION, "DESCRIPCION_INVALIDA", "La descripcion");
	}

	public static Cuenta crear(CuentaId id, UsuarioId propietario, NombreCuenta nombre, String banco, String descripcion) {
		Cuenta cuenta = new Cuenta(id, propietario, nombre, banco, descripcion, 0);
		cuenta.anunciarCambio();
		return cuenta;
	}

	public static Cuenta reconstituir(CuentaId id, UsuarioId propietario, NombreCuenta nombre, String banco,
			String descripcion, long version) {
		return new Cuenta(id, propietario, nombre, banco, descripcion, version);
	}

	/** Cambia los datos descriptivos; si no cambia nada, no hay evento. */
	public void modificar(NombreCuenta nuevoNombre, String nuevoBanco, String nuevaDescripcion) {
		String banco = Textos.opcional(nuevoBanco, LONGITUD_BANCO, "BANCO_INVALIDO", "El banco");
		String descripcion = Textos.opcional(nuevaDescripcion, LONGITUD_DESCRIPCION, "DESCRIPCION_INVALIDA", "La descripcion");

		if (this.nombre.equals(nuevoNombre) && Objects.equals(this.banco, banco) && Objects.equals(this.descripcion, descripcion)) {
			return;
		}

		this.nombre = nuevoNombre;
		this.banco = banco;
		this.descripcion = descripcion;
		this.anunciarCambio();
	}

	public void eliminar() {
		this.registrarEvento(new CuentaEliminada(this.id, this.propietario, this.siguienteVersion()));
	}

	private void anunciarCambio() {
		this.registrarEvento(new CuentaGuardada(this.id, this.propietario, this.siguienteVersion(), this.nombre,
				this.banco, this.descripcion));
	}

	@Override
	public CuentaId id() {
		return this.id;
	}

	@Override
	public UsuarioId propietario() {
		return this.propietario;
	}

	public NombreCuenta nombre() {
		return this.nombre;
	}

	public String banco() {
		return this.banco;
	}

	public String descripcion() {
		return this.descripcion;
	}
}
