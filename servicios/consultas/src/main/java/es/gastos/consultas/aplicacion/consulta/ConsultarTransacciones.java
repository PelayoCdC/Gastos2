package es.gastos.consultas.aplicacion.consulta;

import es.gastos.consultas.dominio.transacciones.FiltroTransacciones;
import es.gastos.consultas.dominio.transacciones.Ordenacion;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.Pagina;
import es.gastos.kernel.aplicacion.PeticionPagina;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.UUID;

/** La rejilla, el detalle y la exportación de las transacciones del usuario. */
@CasoDeUso
public class ConsultarTransacciones {

	/**
	 * Tope de la exportación: unas 50 000 filas son varios años de extractos y caben holgadas en
	 * memoria. Por encima se pide acotar el filtro en vez de generar un Excel enorme.
	 */
	public static final int MAXIMO_EXPORTACION = 50_000;

	private final LecturaTransacciones lectura;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public ConsultarTransacciones(LecturaTransacciones lectura, UnidadDeTrabajo unidadDeTrabajo) {
		this.lectura = lectura;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public Pagina<FilaTransaccion> buscar(UsuarioId usuario, FiltroTransacciones filtro, Ordenacion ordenacion, PeticionPagina pagina) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.lectura.buscar(usuario, filtro, ordenacion, pagina));
	}

	public FilaTransaccion detalle(UsuarioId usuario, UUID id) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.lectura.una(usuario, id))
				.orElseThrow(() -> new RecursoNoEncontrado("la transaccion", id));
	}

	public List<FilaTransaccion> paraExportar(UsuarioId usuario, FiltroTransacciones filtro, Ordenacion ordenacion) {
		List<FilaTransaccion> filas = this.unidadDeTrabajo.ejecutarLectura(
				() -> this.lectura.todas(usuario, filtro, ordenacion, MAXIMO_EXPORTACION + 1));
		if (filas.size() > MAXIMO_EXPORTACION) {
			throw new ExcepcionDominio("EXPORTACION_DEMASIADO_GRANDE",
					"La exportacion admite como mucho " + MAXIMO_EXPORTACION + " filas; acota el filtro");
		}
		return filas;
	}
}
