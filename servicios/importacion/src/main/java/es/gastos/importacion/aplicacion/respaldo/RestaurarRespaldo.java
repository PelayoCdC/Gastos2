package es.gastos.importacion.aplicacion.respaldo;

import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.FormatoRepositorio;
import es.gastos.importacion.dominio.respaldo.FormatosRestaurados;
import es.gastos.importacion.dominio.respaldo.PlanFormatos;
import es.gastos.importacion.dominio.respaldo.RespaldoTraducido;
import es.gastos.importacion.dominio.respaldo.TraductorRespaldoV1;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.ArrayList;
import java.util.List;

/**
 * Restaura un respaldo v1 (del back antiguo, del móvil o de este sistema) en la cuenta del usuario
 * (docs/adr/0020). importacion es la capa anticorrupción: traduce el formato y reparte cada parte a su
 * dueño, que es quien sabe validarla y guardarla.
 * <ul>
 *   <li><b>SIMULAR</b> pide a cada servicio un ensayo: conteos y anomalías, sin tocar nada.</li>
 *   <li><b>SUSTITUIR</b> ensaya primero en todos (si alguno lo rechaza, no se toca nada) y después
 *   restaura en orden movimientos → clasificacion → formatos. Cada paso es atómico
 *   en su servicio, pero el conjunto no: si uno falla, los anteriores ya están hechos. Como cada paso
 *   sustituye, basta con repetir la restauración para llegar al estado correcto.</li>
 * </ul>
 */
@CasoDeUso
public class RestaurarRespaldo {

	public enum Modo {
		SIMULAR, SUSTITUIR
	}

	public record Informe(Modo modo, int cuentas, int documentos, int transacciones, int categorias, int etiquetas, int clasificadas,
			int formatos, List<String> anomalias) {
	}

	private final RestauracionRemota.Movimientos movimientos;

	private final RestauracionRemota.Clasificacion clasificacion;

	private final FormatoRepositorio formatos;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public RestaurarRespaldo(RestauracionRemota.Movimientos movimientos, RestauracionRemota.Clasificacion clasificacion,
			FormatoRepositorio formatos, PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo) {
		this.movimientos = movimientos;
		this.clasificacion = clasificacion;
		this.formatos = formatos;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public Informe ejecutar(UsuarioId usuario, RespaldoV1 respaldo, Modo modo) {
		RespaldoTraducido traducido = TraductorRespaldoV1.traducir(respaldo);

		// Siempre se ensaya primero en todos los servicios: si alguno rechaza el respaldo (por ejemplo, porque
		// es de otra cuenta), no se ha tocado nada en ninguno
		RestauracionRemota.ResumenMovimientos resumenMovimientos = this.movimientos.restaurarMovimientos(usuario, traducido, true);
		RestauracionRemota.ResumenClasificacion resumenClasificacion = this.clasificacion.restaurarClasificacion(usuario, traducido, true);
		PlanFormatos.Plan planFormatos = PlanFormatos.de(usuario, traducido.configuraciones());
		if (this.formatos.algunoDeOtroUsuario(usuario, planFormatos.formatos().stream().map(FormatoBanco::id).toList())) {
			throw new Conflicto("RESPALDO_DE_OTRA_CUENTA",
					"El respaldo contiene datos de otra cuenta de usuario: solo se puede restaurar en la cuenta de la que salio");
		}

		if (modo == Modo.SUSTITUIR) {
			resumenMovimientos = this.movimientos.restaurarMovimientos(usuario, traducido, false);
			resumenClasificacion = this.clasificacion.restaurarClasificacion(usuario, traducido, false);
			this.sustituirFormatos(usuario, planFormatos.formatos());
		}

		List<String> anomalias = new ArrayList<>(traducido.anomalias());
		anomalias.addAll(resumenMovimientos.anomalias());
		anomalias.addAll(resumenClasificacion.anomalias());
		anomalias.addAll(planFormatos.anomalias());
		return new Informe(modo, resumenMovimientos.cuentas(), resumenMovimientos.documentos(), resumenMovimientos.transacciones(),
				resumenClasificacion.categorias(), resumenClasificacion.etiquetas(), resumenClasificacion.clasificadas(),
				planFormatos.formatos().size(), List.copyOf(anomalias));
	}

	private void sustituirFormatos(UsuarioId usuario, List<FormatoBanco> nuevos) {
		this.unidadDeTrabajo.ejecutar(() -> {
			this.formatos.eliminarDelUsuario(usuario);
			this.publicador.publicar(List.of(new FormatosRestaurados(usuario, nuevos.size())));
			for (FormatoBanco formato : nuevos) {
				this.formatos.guardar(formato);
				this.publicador.publicarDe(formato);
			}
		});
	}
}
