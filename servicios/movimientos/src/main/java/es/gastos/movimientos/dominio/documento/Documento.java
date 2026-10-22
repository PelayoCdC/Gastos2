package es.gastos.movimientos.dominio.documento;

import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;

import java.time.LocalDate;
import java.util.Objects;

/** Registro de una importación de extracto. */
public final class Documento extends RaizAgregado<DocumentoId> {

	private final DocumentoId id;

	private final UsuarioId propietario;

	private CuentaId cuentaId;

	private NombreDocumento nombre;

	private final String nombreArchivo;

	private final LocalDate fecha;

	private final String resultadoRegistro;

	private Documento(DocumentoId id, UsuarioId propietario, CuentaId cuentaId, NombreDocumento nombre, String nombreArchivo,
			LocalDate fecha, String resultadoRegistro, long version) {
		super(version);
		this.id = Objects.requireNonNull(id);
		this.propietario = Objects.requireNonNull(propietario);
		this.cuentaId = cuentaId;
		this.nombre = Objects.requireNonNull(nombre);
		this.nombreArchivo = Objects.requireNonNull(nombreArchivo);
		this.fecha = Objects.requireNonNull(fecha);
		this.resultadoRegistro = resultadoRegistro;
	}

	/**
	 * Registra la importación de un extracto. El {@code id} lo genera el cliente (importacion) antes de
	 * llamar y sirve de clave de idempotencia; {@code resultadoRegistro} guarda la respuesta para poder
	 * devolver exactamente lo mismo si la llamada se reintenta.
	 */
	public static Documento registrar(DocumentoId id, UsuarioId propietario, CuentaId cuentaId, NombreDocumento nombre,
			String nombreArchivo, LocalDate fecha, String resultadoRegistro) {
		Objects.requireNonNull(cuentaId, "Un extracto siempre se importa en una cuenta");
		Documento documento = new Documento(id, propietario, cuentaId, nombre, nombreArchivo, fecha, resultadoRegistro, 0);
		documento.registrarEvento(new DocumentoRegistrado(id, propietario, documento.siguienteVersion(), cuentaId, nombre,
				nombreArchivo, fecha));
		return documento;
	}

	/**
	 * Documento que vuelve de un respaldo (docs/adr/0020), con su id original. A diferencia de un
	 * extracto recién importado, puede no tener cuenta (el back antiguo lo permitía) y no guarda
	 * resultado de registro: no hay importación que reintentar.
	 */
	public static Documento restaurar(DocumentoId id, UsuarioId propietario, CuentaId cuentaId, NombreDocumento nombre,
			String nombreArchivo, LocalDate fecha) {
		Documento documento = new Documento(id, propietario, cuentaId, nombre, nombreArchivo, fecha, null, 0);
		documento.registrarEvento(new DocumentoRegistrado(id, propietario, documento.siguienteVersion(), cuentaId, nombre,
				nombreArchivo, fecha));
		return documento;
	}

	/** {@code cuentaId} solo puede ser nulo en documentos migrados del back antiguo. */
	public static Documento reconstituir(DocumentoId id, UsuarioId propietario, CuentaId cuentaId, NombreDocumento nombre,
			String nombreArchivo, LocalDate fecha, String resultadoRegistro, long version) {
		return new Documento(id, propietario, cuentaId, nombre, nombreArchivo, fecha, resultadoRegistro, version);
	}

	public void renombrar(NombreDocumento nuevoNombre) {
		if (this.nombre.equals(nuevoNombre)) {
			return;
		}
		this.nombre = nuevoNombre;
		this.registrarEvento(new DocumentoRenombrado(this.id, this.propietario, this.siguienteVersion(), nuevoNombre));
	}

	/**
	 * Pasa el documento a otra cuenta. Sus transacciones deben moverse en la misma unidad de trabajo:
	 * la invariante "una transacción importada está en la cuenta de su documento" abarca los dos agregados.
	 */
	public void reasignar(CuentaId nuevaCuenta) {
		Objects.requireNonNull(nuevaCuenta);
		if (nuevaCuenta.equals(this.cuentaId)) {
			return;
		}
		CuentaId anterior = this.cuentaId;
		this.cuentaId = nuevaCuenta;
		this.registrarEvento(new DocumentoReasignado(this.id, this.propietario, this.siguienteVersion(), anterior, nuevaCuenta));
	}

	public void eliminar() {
		this.registrarEvento(new DocumentoEliminado(this.id, this.propietario, this.siguienteVersion()));
	}

	@Override
	public DocumentoId id() {
		return this.id;
	}

	@Override
	public UsuarioId propietario() {
		return this.propietario;
	}

	public CuentaId cuentaId() {
		return this.cuentaId;
	}

	public NombreDocumento nombre() {
		return this.nombre;
	}

	public String nombreArchivo() {
		return this.nombreArchivo;
	}

	public LocalDate fecha() {
		return this.fecha;
	}

	/** Resultado guardado de la importación, para responder igual si se reintenta (idempotencia). */
	public String resultadoRegistro() {
		return this.resultadoRegistro;
	}
}
