package es.gastos.consultas.aplicacion.informes;

import es.gastos.consultas.dominio.informes.AgregadorBizum;
import es.gastos.consultas.dominio.informes.Apunte;
import es.gastos.consultas.dominio.informes.DetectorRecurrentes;
import es.gastos.consultas.dominio.informes.Informes;
import es.gastos.consultas.dominio.informes.Periodo;
import es.gastos.consultas.dominio.informes.Seleccion;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Los informes y análisis de la página de inicio y de Análisis. Cada uno carga los movimientos del
 * usuario en el periodo y aplica el calculador puro correspondiente del dominio.
 */
@CasoDeUso
public class ConsultarInformes {

	public static final int LIMITE_TOP_POR_DEFECTO = 15;

	private static final int LIMITE_TOP_MAXIMO = 100;

	private final LecturaApuntes apuntes;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final Clock reloj;

	public ConsultarInformes(LecturaApuntes apuntes, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
		this.apuntes = apuntes;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.reloj = reloj;
	}

	public List<Informes.ResumenMes> resumenMensual(UsuarioId usuario, Periodo periodo, Seleccion seleccion) {
		return Informes.resumenMensual(this.cargar(usuario, periodo), seleccion);
	}

	public List<Informes.SerieSaldo> evolucionSaldo(UsuarioId usuario, Periodo periodo) {
		return Informes.evolucionSaldo(this.cargar(usuario, periodo));
	}

	public List<Informes.TotalCategoria> gastosPorCategoria(UsuarioId usuario, Periodo periodo) {
		return Informes.gastosPorCategoria(this.cargar(usuario, periodo));
	}

	public List<Informes.TotalEtiqueta> gastosPorEtiqueta(UsuarioId usuario, Periodo periodo, Set<UUID> categoriasDeLaEtiqueta) {
		return Informes.gastosPorEtiqueta(this.cargar(usuario, periodo), categoriasDeLaEtiqueta == null ? Set.of() : categoriasDeLaEtiqueta);
	}

	public List<Informes.GastoDia> gastoDiario(UsuarioId usuario, Periodo periodo, Seleccion seleccion) {
		return Informes.gastoDiario(this.cargar(usuario, periodo), seleccion);
	}

	public List<Informes.GastoMesCategoria> gastosMensualesPorCategoria(UsuarioId usuario, Periodo periodo) {
		return Informes.gastosMensualesPorCategoria(this.cargar(usuario, periodo));
	}

	public List<Informes.TopConcepto> topConceptos(UsuarioId usuario, Periodo periodo, Seleccion seleccion, int limite) {
		if (limite < 1 || limite > LIMITE_TOP_MAXIMO) {
			throw new ExcepcionDominio("LIMITE_INVALIDO", "El limite debe estar entre 1 y " + LIMITE_TOP_MAXIMO);
		}
		return Informes.topConceptos(this.cargar(usuario, periodo), seleccion, limite);
	}

	/** Siempre sobre todo el histórico: una suscripción se reconoce por su repetición en el tiempo. */
	public List<DetectorRecurrentes.Recurrente> recurrentes(UsuarioId usuario, Seleccion seleccion) {
		return DetectorRecurrentes.detectar(this.cargar(usuario, Periodo.TODO), seleccion, LocalDate.now(this.reloj));
	}

	public List<AgregadorBizum.ContactoBizum> bizum(UsuarioId usuario, Periodo periodo, Seleccion seleccion) {
		return AgregadorBizum.agrupar(this.cargar(usuario, periodo), seleccion);
	}

	private List<Apunte> cargar(UsuarioId usuario, Periodo periodo) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.apuntes.de(usuario, periodo));
	}
}
