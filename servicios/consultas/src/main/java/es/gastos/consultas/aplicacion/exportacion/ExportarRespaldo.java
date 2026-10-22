package es.gastos.consultas.aplicacion.exportacion;

import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.UsuarioId;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Respaldo completo del usuario en formato v1 (docs/adr/0020): el mismo JSON que generaba el back
 * antiguo, para copia de seguridad y para cargarlo en la app móvil. Sale de las proyecciones, así que
 * refleja lo publicado hasta ese momento (con {@code Gastos-Esperar} se puede pedir que incluya lo
 * último que se haya escrito).
 */
@CasoDeUso
public class ExportarRespaldo {

	private final LecturaRespaldo lectura;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final Clock reloj;

	public ExportarRespaldo(LecturaRespaldo lectura, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
		this.lectura = lectura;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.reloj = reloj;
	}

	public RespaldoV1 exportar(UsuarioId usuario) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.lectura.de(usuario, LocalDate.now(this.reloj)));
	}
}
