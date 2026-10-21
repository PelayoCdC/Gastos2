package es.gastos.movimientos.dominio.transaccion;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;
import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.comun.Textos;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;

import java.time.LocalDate;
import java.util.Objects;

/** Un movimiento bancario: hechos, sin clasificación. */
public final class Transaccion extends RaizAgregado<TransaccionId> {

	public static final int LONGITUD_NOTA = 500;

	/** Estado completo de los hechos de la transacción (lo que viaja en sus eventos). */
	public record Datos(TransaccionId id, CuentaId cuentaId, DocumentoId documentoId, LocalDate fechaContable,
			LocalDate fechaValor, Concepto concepto, String nota, Importe importe, Importe saldo, int orden,
			int ordenDocumento, Origen origen, ClaveDuplicado claveDuplicado) {

		public Datos {
			Objects.requireNonNull(id);
			Objects.requireNonNull(fechaContable, "La fecha contable es obligatoria");
			Objects.requireNonNull(concepto);
			Objects.requireNonNull(importe, "El importe es obligatorio");
			Objects.requireNonNull(origen);
			Objects.requireNonNull(claveDuplicado);
			nota = Textos.opcional(nota, LONGITUD_NOTA, "NOTA_INVALIDA", "La nota");
		}
	}

	private final UsuarioId propietario;

	private Datos datos;

	private Transaccion(UsuarioId propietario, Datos datos, long version) {
		super(version);
		this.propietario = Objects.requireNonNull(propietario);
		this.datos = Objects.requireNonNull(datos);
	}

	/** Alta a mano: sin documento, sin saldo (el banco no lo ha dicho) y con orden 0. */
	public static Transaccion registrarManual(TransaccionId id, UsuarioId propietario, CuentaId cuentaId,
			LocalDate fechaContable, LocalDate fechaValor, Concepto concepto, String nota, Importe importe) {
		Datos datos = new Datos(id, cuentaId, null, fechaContable, fechaValor, concepto, nota, importe, null, 0, 0,
				Origen.MANUAL, ClaveDuplicado.de(fechaContable, importe, concepto));
		Transaccion transaccion = new Transaccion(propietario, datos, 0);
		transaccion.registrarEvento(new TransaccionRegistrada(propietario, transaccion.siguienteVersion(), datos));
		return transaccion;
	}

	public static Transaccion reconstituir(UsuarioId propietario, Datos datos, long version) {
		return new Transaccion(propietario, datos, version);
	}

	/**
	 * Corrige los hechos editables. La cuenta de una transacción importada no se cambia aquí sino
	 * reasignando su documento (si no, documento y transacción quedarían en cuentas distintas). La
	 * clave de duplicado de una importada no cambia: identifica lo que dijo el banco, no lo que
	 * corrigió el usuario; la de una manual sí se recalcula.
	 */
	public void corregir(CuentaId cuentaId, LocalDate fechaContable, LocalDate fechaValor, Concepto concepto, String nota,
			Importe importe) {
		if (this.datos.documentoId() != null && !Objects.equals(cuentaId, this.datos.cuentaId())) {
			throw new ExcepcionDominio("CUENTA_DE_TRANSACCION_IMPORTADA",
					"La cuenta de una transaccion importada se cambia reasignando su documento");
		}

		ClaveDuplicado clave = this.datos.origen() == Origen.MANUAL
				? ClaveDuplicado.de(fechaContable, importe, concepto)
				: this.datos.claveDuplicado();
		Datos corregidos = new Datos(this.datos.id(), cuentaId, this.datos.documentoId(), fechaContable, fechaValor,
				concepto, nota, importe, this.datos.saldo(), this.datos.orden(), this.datos.ordenDocumento(),
				this.datos.origen(), clave);

		if (corregidos.equals(this.datos)) {
			return;
		}

		this.datos = corregidos;
		this.registrarEvento(new TransaccionCorregida(this.propietario, this.siguienteVersion(), corregidos));
	}

	public void eliminar() {
		this.registrarEvento(new TransaccionEliminada(this.id(), this.propietario, this.siguienteVersion(), this.datos.documentoId()));
	}

	@Override
	public TransaccionId id() {
		return this.datos.id();
	}

	@Override
	public UsuarioId propietario() {
		return this.propietario;
	}

	public Datos datos() {
		return this.datos;
	}
}
