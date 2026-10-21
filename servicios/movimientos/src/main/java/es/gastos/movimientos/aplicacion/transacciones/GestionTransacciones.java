package es.gastos.movimientos.aplicacion.transacciones;

import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.cuenta.CuentaRepositorio;
import es.gastos.movimientos.dominio.transaccion.Concepto;
import es.gastos.movimientos.dominio.transaccion.Transaccion;
import es.gastos.movimientos.dominio.transaccion.TransaccionId;
import es.gastos.movimientos.dominio.transaccion.TransaccionRepositorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Casos de uso de los hechos de una transacción: alta manual, corrección, borrado y consulta.
 * La categoría y las etiquetas se gestionan en el contexto de clasificación (docs/adr/0002).
 */
@CasoDeUso
public class GestionTransacciones {

	/** Hechos editables de una transacción. {@code cuentaId} es opcional en las manuales. */
	public record Hechos(UUID cuentaId, LocalDate fechaContable, LocalDate fechaValor, String concepto, String nota,
			BigDecimal importe) {
	}

	private final TransaccionRepositorio transacciones;

	private final CuentaRepositorio cuentas;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	public GestionTransacciones(TransaccionRepositorio transacciones, CuentaRepositorio cuentas, PublicadorEventos publicador,
			UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids) {
		this.transacciones = transacciones;
		this.cuentas = cuentas;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
	}

	public DatosTransaccion registrarManual(UsuarioId usuario, Hechos hechos) {
		exigirFecha(hechos);
		Importe importe = importe(hechos.importe());
		Concepto concepto = new Concepto(hechos.concepto());

		return this.unidadDeTrabajo.ejecutar(() -> {
			CuentaId cuenta = this.cuentaPropia(usuario, hechos.cuentaId());
			Transaccion transaccion = Transaccion.registrarManual(new TransaccionId(this.ids.nuevo()), usuario, cuenta,
					hechos.fechaContable(), hechos.fechaValor(), concepto, hechos.nota(), importe);
			this.transacciones.guardar(transaccion);
			this.publicador.publicarDe(transaccion);
			return DatosTransaccion.de(transaccion);
		});
	}

	public DatosTransaccion corregir(UsuarioId usuario, TransaccionId id, Hechos hechos) {
		exigirFecha(hechos);
		Importe importe = importe(hechos.importe());
		Concepto concepto = new Concepto(hechos.concepto());

		return this.unidadDeTrabajo.ejecutar(() -> {
			Transaccion transaccion = this.propia(usuario, id);
			CuentaId cuenta = DatosTransaccion.cuenta(hechos.cuentaId());
			if (!Objects.equals(cuenta, transaccion.datos().cuentaId())) {
				this.cuentaPropia(usuario, hechos.cuentaId());
			}
			transaccion.corregir(cuenta, hechos.fechaContable(), hechos.fechaValor(), concepto, hechos.nota(), importe);
			this.transacciones.guardar(transaccion);
			this.publicador.publicarDe(transaccion);
			return DatosTransaccion.de(transaccion);
		});
	}

	public void eliminar(UsuarioId usuario, TransaccionId id) {
		this.unidadDeTrabajo.ejecutar(() -> {
			Transaccion transaccion = this.propia(usuario, id);
			transaccion.eliminar();
			this.transacciones.eliminar(transaccion);
			this.publicador.publicarDe(transaccion);
		});
	}

	public DatosTransaccion consultar(UsuarioId usuario, TransaccionId id) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> DatosTransaccion.de(this.propia(usuario, id)));
	}

	private Transaccion propia(UsuarioId usuario, TransaccionId id) {
		return Propiedad.exigir(this.transacciones.buscar(id), usuario, "la transaccion", id);
	}

	private CuentaId cuentaPropia(UsuarioId usuario, UUID cuentaId) {
		if (cuentaId == null) {
			return null;
		}
		CuentaId cuenta = new CuentaId(cuentaId);
		Propiedad.exigir(this.cuentas.buscar(cuenta), usuario, "la cuenta", cuenta);
		return cuenta;
	}

	private static void exigirFecha(Hechos hechos) {
		if (hechos.fechaContable() == null) {
			throw new ExcepcionDominio("FECHA_INVALIDA", "La fecha contable es obligatoria");
		}
	}

	private static Importe importe(BigDecimal valor) {
		if (valor == null) {
			throw new ExcepcionDominio("IMPORTE_INVALIDO", "El importe es obligatorio");
		}
		return Importe.de(valor);
	}
}
