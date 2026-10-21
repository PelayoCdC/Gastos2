package es.gastos.movimientos.aplicacion.extractos;

import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.cuenta.CuentaRepositorio;
import es.gastos.movimientos.dominio.documento.Documento;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import es.gastos.movimientos.dominio.documento.DocumentoRepositorio;
import es.gastos.movimientos.dominio.documento.NombreDocumento;
import es.gastos.movimientos.dominio.transaccion.ClaveDuplicado;
import es.gastos.movimientos.dominio.transaccion.Concepto;
import es.gastos.movimientos.dominio.transaccion.DetectorDuplicados;
import es.gastos.movimientos.dominio.transaccion.LineaExtracto;
import es.gastos.movimientos.dominio.transaccion.Transaccion;
import es.gastos.movimientos.dominio.transaccion.TransaccionId;
import es.gastos.movimientos.dominio.transaccion.TransaccionRepositorio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registra un extracto ya interpretado por importacion: crea el documento y las transacciones de
 * las filas nuevas, omitiendo las ya registradas (docs/adr/0015). Es la parte "proveedor" de la
 * relación cliente/proveedor con importacion (docs/adr/0003).
 *
 * <p>Garantías:
 * <ul>
 *   <li><b>Idempotente</b> por {@code extractoId}: un reintento (por ejemplo, tras un timeout de red)
 *   devuelve el resultado guardado sin volver a importar nada.</li>
 *   <li><b>Serializado por cuenta</b>: bloquea la fila de la cuenta, así dos importaciones simultáneas
 *   del mismo extracto no pueden ver ambas "ninguna fila existe" e importarlo dos veces.</li>
 *   <li><b>Orden de eventos</b>: {@code DocumentoRegistrado} antes que las {@code TransaccionRegistrada}.</li>
 * </ul>
 */
@CasoDeUso
public class RegistrarExtracto {

	public record Comando(UsuarioId usuario, UUID extractoId, UUID cuentaId, String nombre, String nombreArchivo,
			List<Linea> lineas) {
	}

	public record Linea(int fila, LocalDate fechaContable, LocalDate fechaValor, String concepto, BigDecimal importe,
			BigDecimal saldo) {
	}

	private final CuentaRepositorio cuentas;

	private final DocumentoRepositorio documentos;

	private final TransaccionRepositorio transacciones;

	private final CodificadorResultados codificador;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final GeneradorIds ids;

	private final Clock reloj;

	public RegistrarExtracto(CuentaRepositorio cuentas, DocumentoRepositorio documentos, TransaccionRepositorio transacciones,
			CodificadorResultados codificador, PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids,
			Clock reloj) {
		this.cuentas = cuentas;
		this.documentos = documentos;
		this.transacciones = transacciones;
		this.codificador = codificador;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.ids = ids;
		this.reloj = reloj;
	}

	public ResultadoExtracto ejecutar(Comando comando) {
		if (comando.extractoId() == null || comando.cuentaId() == null || comando.nombreArchivo() == null) {
			throw new ExcepcionDominio("EXTRACTO_INVALIDO", "Faltan el id del extracto, la cuenta o el nombre del fichero");
		}
		List<LineaExtracto> lineas = comando.lineas().stream().map(RegistrarExtracto::linea).toList();
		DocumentoId documentoId = new DocumentoId(comando.extractoId());
		CuentaId cuentaId = new CuentaId(comando.cuentaId());
		UsuarioId usuario = comando.usuario();

		return this.unidadDeTrabajo.ejecutar(() -> {
			// El bloqueo de la cuenta serializa las importaciones concurrentes en ella
			Propiedad.exigir(this.cuentas.buscarParaActualizar(cuentaId), usuario, "la cuenta", cuentaId);

			Optional<Documento> yaRegistrado = this.documentos.buscar(documentoId);
			if (yaRegistrado.isPresent()) {
				Documento documento = Propiedad.exigir(yaRegistrado, usuario, "el extracto", documentoId);
				if (documento.resultadoRegistro() == null) {
					throw new RecursoNoEncontrado("el extracto", documentoId);
				}
				return this.codificador.decodificar(documento.resultadoRegistro());
			}

			Set<ClaveDuplicado> claves = lineas.stream().map(LineaExtracto::clave).collect(Collectors.toSet());
			Map<ClaveDuplicado, Long> existentes = this.transacciones.contarClaves(usuario, cuentaId, claves);
			DetectorDuplicados.Separacion separacion = DetectorDuplicados.separar(lineas, existentes);

			NombreDocumento nombre = new NombreDocumento(
					comando.nombre() == null || comando.nombre().isBlank() ? comando.nombreArchivo() : comando.nombre());
			ResultadoExtracto resultado = new ResultadoExtracto(documentoId.valor(), nombre.valor(), comando.nombreArchivo(),
					separacion.nuevas().size(), separacion.duplicadas().stream().map(RegistrarExtracto::duplicada).toList());

			Documento documento = Documento.registrar(documentoId, usuario, cuentaId, nombre, comando.nombreArchivo(),
					LocalDate.now(this.reloj), this.codificador.codificar(resultado));
			this.documentos.guardar(documento);
			this.publicador.publicarDe(documento);

			List<Transaccion> nuevas = new ArrayList<>();
			for (LineaExtracto linea : separacion.nuevas()) {
				nuevas.add(Transaccion.desdeLineaExtracto(new TransaccionId(this.ids.nuevo()), usuario, cuentaId, documentoId,
						linea, nuevas.size() + 1));
			}
			this.transacciones.insertarTodas(nuevas);
			nuevas.forEach(this.publicador::publicarDe);

			return resultado;
		});
	}

	private static LineaExtracto linea(Linea linea) {
		if (linea.importe() == null || linea.fechaContable() == null) {
			throw new ExcepcionDominio("LINEA_INVALIDA", "La fila " + linea.fila() + " no trae fecha contable o importe");
		}
		return new LineaExtracto(linea.fila(), linea.fechaContable(), linea.fechaValor(), new Concepto(linea.concepto()),
				Importe.de(linea.importe()), linea.saldo() == null ? null : Importe.de(linea.saldo()));
	}

	private static ResultadoExtracto.Duplicada duplicada(LineaExtracto linea) {
		return new ResultadoExtracto.Duplicada(linea.fila(), linea.fechaContable(), linea.concepto().valor(), linea.importe().valor());
	}
}
