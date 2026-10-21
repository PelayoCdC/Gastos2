package es.gastos.importacion.aplicacion;

import es.gastos.importacion.dominio.extracto.Hoja;
import es.gastos.importacion.dominio.extracto.Interpretacion;
import es.gastos.importacion.dominio.extracto.InterpreteExtracto;
import es.gastos.importacion.dominio.extracto.Previsualizacion;
import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.importacion.dominio.formato.FormatoRepositorio;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.UUID;

/**
 * Previsualizar e importar extractos: leer el fichero, interpretarlo con el formato del banco y
 * entregar las líneas limpias a movimientos. Este servicio no guarda movimientos: no tiene unidad de
 * trabajo propia en la importación, solo lee su formato.
 */
@CasoDeUso
public class ImportarExtractos {

	public record Comando(UsuarioId usuario, UUID formatoId, UUID cuentaId, String nombre, String nombreArchivo, byte[] contenido) {
	}

	/** Lo que devuelve la importación: lo que registró movimientos más los errores de interpretación. */
	public record Resultado(UUID documentoId, String nombreDocumento, String nombreArchivo, int importadas,
			List<Interpretacion.ErrorFila> errores, List<RegistroMovimientos.Duplicada> duplicados) {
	}

	private final FormatoRepositorio formatos;

	private final LectorHojas lector;

	private final RegistroMovimientos movimientos;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	public ImportarExtractos(FormatoRepositorio formatos, LectorHojas lector, RegistroMovimientos movimientos,
			UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids) {
		this.formatos = formatos;
		this.lector = lector;
		this.movimientos = movimientos;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
	}

	public Previsualizacion previsualizar(String nombreArchivo, byte[] contenido) {
		return Previsualizacion.de(nombreArchivo, this.leer(nombreArchivo, contenido));
	}

	public Resultado importar(Comando comando) {
		if (comando.cuentaId() == null || comando.formatoId() == null) {
			throw new ExcepcionDominio("IMPORTACION_INVALIDA", "Hay que indicar el formato y la cuenta");
		}
		FormatoId formatoId = new FormatoId(comando.formatoId());
		FormatoBanco formato = this.unidadDeTrabajo.ejecutarLectura(() ->
				Propiedad.exigir(this.formatos.buscar(formatoId), comando.usuario(), "el formato", formatoId));

		Interpretacion interpretacion = InterpreteExtracto.interpretar(this.leer(comando.nombreArchivo(), comando.contenido()), formato);

		RegistroMovimientos.Registro registro = this.movimientos.registrar(comando.usuario(), new RegistroMovimientos.Extracto(
				this.ids.nuevo(), comando.cuentaId(), comando.nombre(), comando.nombreArchivo(), interpretacion.lineas()));

		return new Resultado(registro.documentoId(), registro.nombreDocumento(), registro.nombreArchivo(), registro.importadas(),
				interpretacion.errores(), registro.duplicados());
	}

	private Hoja leer(String nombreArchivo, byte[] contenido) {
		if (contenido == null || contenido.length == 0) {
			throw new ExcepcionDominio("FICHERO_VACIO", "El fichero esta vacio");
		}
		return this.lector.leer(nombreArchivo == null ? "extracto" : nombreArchivo, contenido);
	}
}
