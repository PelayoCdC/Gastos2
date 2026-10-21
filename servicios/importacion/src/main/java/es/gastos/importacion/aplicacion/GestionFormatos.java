package es.gastos.importacion.aplicacion;

import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.FormatoFecha;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.importacion.dominio.formato.FormatoRepositorio;
import es.gastos.importacion.dominio.formato.MapeoColumnas;
import es.gastos.importacion.dominio.formato.NombreFormato;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.UUID;

/** Alta, modificación, consulta y borrado de formatos de banco (nombre único por usuario). */
@CasoDeUso
public class GestionFormatos {

	/** Datos de un formato; {@code filaInicio} nulo significa 1. */
	public record Datos(String nombre, Integer filaInicio, Integer columnaConcepto, Integer columnaImporte,
			Integer columnaFechaContable, Integer columnaFechaValor, Integer columnaSaldo, String formatoFecha) {
	}

	public record DatosFormato(UUID id, String nombre, int filaInicio, int columnaConcepto, int columnaImporte,
			int columnaFechaContable, Integer columnaFechaValor, Integer columnaSaldo, String formatoFecha) {

		static DatosFormato de(FormatoBanco formato) {
			MapeoColumnas c = formato.columnas();
			return new DatosFormato(formato.id().valor(), formato.nombre().valor(), formato.filaInicio(), c.concepto(), c.importe(),
					c.fechaContable(), c.fechaValor(), c.saldo(), formato.formatoFecha().patron());
		}
	}

	private final FormatoRepositorio formatos;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	public GestionFormatos(FormatoRepositorio formatos, PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo,
			GeneradorIds ids) {
		this.formatos = formatos;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
	}

	public DatosFormato crear(UsuarioId usuario, Datos datos) {
		NombreFormato nombre = new NombreFormato(datos.nombre());
		MapeoColumnas columnas = columnas(datos);
		FormatoFecha formatoFecha = new FormatoFecha(datos.formatoFecha());

		return this.unidadDeTrabajo.ejecutar(() -> {
			this.exigirNombreLibre(usuario, nombre, null);
			FormatoBanco formato = FormatoBanco.crear(new FormatoId(this.ids.nuevo()), usuario, nombre, filaInicio(datos), columnas,
					formatoFecha);
			this.formatos.guardar(formato);
			this.publicador.publicarDe(formato);
			return DatosFormato.de(formato);
		});
	}

	public DatosFormato modificar(UsuarioId usuario, FormatoId id, Datos datos) {
		NombreFormato nombre = new NombreFormato(datos.nombre());
		MapeoColumnas columnas = columnas(datos);
		FormatoFecha formatoFecha = new FormatoFecha(datos.formatoFecha());

		return this.unidadDeTrabajo.ejecutar(() -> {
			FormatoBanco formato = Propiedad.exigir(this.formatos.buscar(id), usuario, "el formato", id);
			this.exigirNombreLibre(usuario, nombre, id);
			formato.modificar(nombre, filaInicio(datos), columnas, formatoFecha);
			this.formatos.guardar(formato);
			this.publicador.publicarDe(formato);
			return DatosFormato.de(formato);
		});
	}

	public List<DatosFormato> listar(UsuarioId usuario) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.formatos.delUsuario(usuario).stream().map(DatosFormato::de).toList());
	}

	public DatosFormato consultar(UsuarioId usuario, FormatoId id) {
		return this.unidadDeTrabajo.ejecutarLectura(() ->
				DatosFormato.de(Propiedad.exigir(this.formatos.buscar(id), usuario, "el formato", id)));
	}

	public void eliminar(UsuarioId usuario, FormatoId id) {
		this.unidadDeTrabajo.ejecutar(() -> {
			FormatoBanco formato = Propiedad.exigir(this.formatos.buscar(id), usuario, "el formato", id);
			formato.eliminar();
			this.formatos.eliminar(formato);
			this.publicador.publicarDe(formato);
		});
	}

	private void exigirNombreLibre(UsuarioId usuario, NombreFormato nombre, FormatoId excepto) {
		if (this.formatos.existeNombre(usuario, nombre.normalizado(), excepto)) {
			throw Conflicto.nombreDuplicado("un formato", nombre.valor());
		}
	}

	private static MapeoColumnas columnas(Datos datos) {
		return new MapeoColumnas(datos.columnaConcepto(), datos.columnaImporte(), datos.columnaFechaContable(),
				datos.columnaFechaValor(), datos.columnaSaldo());
	}

	private static int filaInicio(Datos datos) {
		return datos.filaInicio() == null ? 1 : datos.filaInicio();
	}
}
