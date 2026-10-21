package es.gastos.movimientos.aplicacion.cuentas;

import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.Cuenta;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.cuenta.CuentaRepositorio;
import es.gastos.movimientos.dominio.documento.Documento;
import es.gastos.movimientos.dominio.documento.DocumentoRepositorio;
import es.gastos.movimientos.dominio.transaccion.Transaccion;
import es.gastos.movimientos.dominio.transaccion.TransaccionRepositorio;

import java.util.List;

/**
 * Borra una cuenta. Si tiene movimientos, por defecto se niega (409 {@code CUENTA_CON_MOVIMIENTOS})
 * para que nadie pierda datos sin querer; con {@code conMovimientos} borra en cascada sus documentos
 * (con sus transacciones) y sus transacciones sueltas, emitiendo los mismos eventos que si se
 * borraran uno a uno, de modo que los consumidores no necesitan lógica nueva.
 */
@CasoDeUso
public class EliminarCuenta {

	private final CuentaRepositorio cuentas;

	private final DocumentoRepositorio documentos;

	private final TransaccionRepositorio transacciones;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public EliminarCuenta(CuentaRepositorio cuentas, DocumentoRepositorio documentos, TransaccionRepositorio transacciones,
			PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo) {
		this.cuentas = cuentas;
		this.documentos = documentos;
		this.transacciones = transacciones;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public void ejecutar(UsuarioId usuario, CuentaId id, boolean conMovimientos) {
		this.unidadDeTrabajo.ejecutar(() -> {
			Cuenta cuenta = Propiedad.exigir(this.cuentas.buscar(id), usuario, "la cuenta", id);
			List<Documento> documentosDeLaCuenta = this.documentos.deCuenta(id);
			long numeroTransacciones = this.transacciones.contarEnCuenta(id);

			if (!conMovimientos && (!documentosDeLaCuenta.isEmpty() || numeroTransacciones > 0)) {
				throw new Conflicto("CUENTA_CON_MOVIMIENTOS", "La cuenta tiene " + documentosDeLaCuenta.size()
						+ " documentos y " + numeroTransacciones + " transacciones; para borrarlos tambien usa conMovimientos=true");
			}

			for (Documento documento : documentosDeLaCuenta) {
				this.transacciones.eliminarDeDocumento(documento.id());
				documento.eliminar();
				this.documentos.eliminar(documento);
				this.publicador.publicarDe(documento);
			}

			for (Transaccion transaccion : this.transacciones.sinDocumentoDeCuenta(id)) {
				transaccion.eliminar();
				this.transacciones.eliminar(transaccion);
				this.publicador.publicarDe(transaccion);
			}

			cuenta.eliminar();
			this.cuentas.eliminar(cuenta);
			this.publicador.publicarDe(cuenta);
		});
	}
}
