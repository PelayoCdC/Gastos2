package es.gastos.movimientos.aplicacion.respaldo;

import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.Cuenta;
import es.gastos.movimientos.dominio.cuenta.CuentaRepositorio;
import es.gastos.movimientos.dominio.documento.Documento;
import es.gastos.movimientos.dominio.documento.DocumentoRepositorio;
import es.gastos.movimientos.dominio.respaldo.MovimientosRestaurados;
import es.gastos.movimientos.dominio.respaldo.PlanRestauracion;
import es.gastos.movimientos.dominio.transaccion.TransaccionRepositorio;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Sustituye todos los movimientos del usuario por los de un respaldo (docs/adr/0020), o solo informa de
 * lo que haría ({@code simular}). Es la parte de movimientos de la restauración que orquesta importacion.
 *
 * <p>Todo en una transacción: se borran las transacciones, documentos y cuentas del usuario y se
 * insertan las del respaldo con sus ids originales. Primero se publica {@code MovimientosRestaurados}
 * (para que los consumidores olviden lo anterior) y después el alta de cada cosa. Repetir la misma
 * restauración deja exactamente el mismo estado.
 */
@CasoDeUso
public class RestaurarMovimientos {

	public record Comando(UsuarioId usuario, List<PlanRestauracion.CuentaRespaldo> cuentas, List<PlanRestauracion.DocumentoRespaldo> documentos,
			List<PlanRestauracion.TransaccionRespaldo> transacciones, boolean simular) {
	}

	public record Resumen(int cuentas, int documentos, int transacciones, List<String> anomalias) {
	}

	private final CuentaRepositorio cuentas;

	private final DocumentoRepositorio documentos;

	private final TransaccionRepositorio transacciones;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final Clock reloj;

	public RestaurarMovimientos(CuentaRepositorio cuentas, DocumentoRepositorio documentos, TransaccionRepositorio transacciones,
			PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
		this.cuentas = cuentas;
		this.documentos = documentos;
		this.transacciones = transacciones;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.reloj = reloj;
	}

	public Resumen ejecutar(Comando comando) {
		PlanRestauracion.Plan plan = PlanRestauracion.de(comando.usuario(), lista(comando.cuentas()), lista(comando.documentos()),
				lista(comando.transacciones()), LocalDate.now(this.reloj));
		Resumen resumen = new Resumen(plan.cuentas().size(), plan.documentos().size(), plan.transacciones().size(), plan.anomalias());

		this.unidadDeTrabajo.ejecutar(() -> {
			UsuarioId usuario = comando.usuario();
			this.exigirIdsLibres(usuario, plan);
			if (comando.simular()) {
				return;
			}
			this.transacciones.eliminarDelUsuario(usuario);
			this.documentos.eliminarDelUsuario(usuario);
			this.cuentas.eliminarDelUsuario(usuario);
			this.publicador.publicar(List.of(new MovimientosRestaurados(usuario, resumen.cuentas(), resumen.documentos(),
					resumen.transacciones())));

			for (Cuenta cuenta : plan.cuentas()) {
				this.cuentas.guardar(cuenta);
				this.publicador.publicarDe(cuenta);
			}
			for (Documento documento : plan.documentos()) {
				this.documentos.guardar(documento);
				this.publicador.publicarDe(documento);
			}
			this.transacciones.insertarTodas(plan.transacciones());
			plan.transacciones().forEach(this.publicador::publicarDe);
		});
		return resumen;
	}

	/**
	 * Los ids del respaldo se conservan (docs/adr/0020), así que no pueden estar ya en uso por otra cuenta:
	 * un respaldo solo se restaura en la cuenta de la que salió o en una donde sus ids estén libres (por
	 * ejemplo, la migración desde el back antiguo). Se comprueba antes de borrar nada, y también al simular.
	 */
	private void exigirIdsLibres(UsuarioId usuario, PlanRestauracion.Plan plan) {
		boolean ajenos = this.cuentas.algunoDeOtroUsuario(usuario, plan.cuentas().stream().map(c -> c.id().valor()).toList())
				|| this.documentos.algunoDeOtroUsuario(usuario, plan.documentos().stream().map(d -> d.id().valor()).toList())
				|| this.transacciones.algunoDeOtroUsuario(usuario, plan.transacciones().stream().map(t -> t.id().valor()).toList());
		if (ajenos) {
			throw new Conflicto("RESPALDO_DE_OTRA_CUENTA",
					"El respaldo contiene datos de otra cuenta de usuario: solo se puede restaurar en la cuenta de la que salio");
		}
	}

	private static <T> List<T> lista(List<T> lista) {
		return lista == null ? List.of() : lista;
	}
}
