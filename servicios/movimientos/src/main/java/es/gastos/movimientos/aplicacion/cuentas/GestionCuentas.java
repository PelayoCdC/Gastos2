package es.gastos.movimientos.aplicacion.cuentas;

import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.Cuenta;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.cuenta.CuentaRepositorio;
import es.gastos.movimientos.dominio.cuenta.NombreCuenta;

import java.util.List;

/**
 * Casos de uso de alta, modificación y consulta de cuentas. Están juntos porque comparten la regla
 * de unicidad del nombre; el borrado, con su política de cascada, está en {@link EliminarCuenta}.
 */
@CasoDeUso
public class GestionCuentas {

	public record DatosCuenta(String nombre, String banco, String descripcion) {
	}

	private final CuentaRepositorio cuentas;

	private final LecturaCuentas lectura;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	public GestionCuentas(CuentaRepositorio cuentas, LecturaCuentas lectura, PublicadorEventos publicador,
			UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids) {
		this.cuentas = cuentas;
		this.lectura = lectura;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
	}

	public CuentaId crear(UsuarioId usuario, DatosCuenta datos) {
		NombreCuenta nombre = new NombreCuenta(datos.nombre());

		return this.unidadDeTrabajo.ejecutar(() -> {
			this.exigirNombreLibre(usuario, nombre, null);
			Cuenta cuenta = Cuenta.crear(new CuentaId(this.ids.nuevo()), usuario, nombre, datos.banco(), datos.descripcion());
			this.cuentas.guardar(cuenta);
			this.publicador.publicarDe(cuenta);
			return cuenta.id();
		});
	}

	public void modificar(UsuarioId usuario, CuentaId id, DatosCuenta datos) {
		NombreCuenta nombre = new NombreCuenta(datos.nombre());

		this.unidadDeTrabajo.ejecutar(() -> {
			Cuenta cuenta = Propiedad.exigir(this.cuentas.buscar(id), usuario, "la cuenta", id);
			this.exigirNombreLibre(usuario, nombre, id);
			cuenta.modificar(nombre, datos.banco(), datos.descripcion());
			this.cuentas.guardar(cuenta);
			this.publicador.publicarDe(cuenta);
		});
	}

	public List<ResumenCuenta> listar(UsuarioId usuario) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.lectura.listar(usuario));
	}

	public ResumenCuenta consultar(UsuarioId usuario, CuentaId id) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.lectura.resumen(usuario, id))
				.orElseThrow(() -> new RecursoNoEncontrado("la cuenta", id));
	}

	private void exigirNombreLibre(UsuarioId usuario, NombreCuenta nombre, CuentaId excepto) {
		if (this.cuentas.existeNombre(usuario, nombre.normalizado(), excepto)) {
			throw Conflicto.nombreDuplicado("una cuenta", nombre.valor());
		}
	}
}
