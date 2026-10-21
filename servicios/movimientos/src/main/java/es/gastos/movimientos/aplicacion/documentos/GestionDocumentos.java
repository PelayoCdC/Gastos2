package es.gastos.movimientos.aplicacion.documentos;

import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.Pagina;
import es.gastos.kernel.aplicacion.PeticionPagina;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.cuenta.CuentaRepositorio;
import es.gastos.movimientos.dominio.documento.Documento;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import es.gastos.movimientos.dominio.documento.DocumentoRepositorio;
import es.gastos.movimientos.dominio.documento.NombreDocumento;
import es.gastos.movimientos.dominio.transaccion.TransaccionRepositorio;

/**
 * Casos de uso sobre documentos ya importados. Los documentos se crean solo importando un extracto
 * ({@code RegistrarExtracto}); aquí se renombran, se cambian de cuenta y se borran. Reasignar y
 * borrar arrastran a todas sus transacciones en la misma unidad de trabajo (operación por conjuntos).
 */
@CasoDeUso
public class GestionDocumentos {

	private final DocumentoRepositorio documentos;

	private final CuentaRepositorio cuentas;

	private final TransaccionRepositorio transacciones;

	private final LecturaDocumentos lectura;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public GestionDocumentos(DocumentoRepositorio documentos, CuentaRepositorio cuentas, TransaccionRepositorio transacciones,
			LecturaDocumentos lectura, PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo) {
		this.documentos = documentos;
		this.cuentas = cuentas;
		this.transacciones = transacciones;
		this.lectura = lectura;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public void renombrar(UsuarioId usuario, DocumentoId id, String nombre) {
		NombreDocumento nuevoNombre = new NombreDocumento(nombre);

		this.unidadDeTrabajo.ejecutar(() -> {
			Documento documento = this.propio(usuario, id);
			documento.renombrar(nuevoNombre);
			this.documentos.guardar(documento);
			this.publicador.publicarDe(documento);
		});
	}

	public void reasignarCuenta(UsuarioId usuario, DocumentoId id, CuentaId cuenta) {
		this.unidadDeTrabajo.ejecutar(() -> {
			Propiedad.exigir(this.cuentas.buscar(cuenta), usuario, "la cuenta", cuenta);
			Documento documento = this.propio(usuario, id);
			documento.reasignar(cuenta);
			this.documentos.guardar(documento);
			this.transacciones.moverDocumentoACuenta(id, cuenta);
			this.publicador.publicarDe(documento);
		});
	}

	public void eliminar(UsuarioId usuario, DocumentoId id) {
		this.unidadDeTrabajo.ejecutar(() -> {
			Documento documento = this.propio(usuario, id);
			this.transacciones.eliminarDeDocumento(id);
			documento.eliminar();
			this.documentos.eliminar(documento);
			this.publicador.publicarDe(documento);
		});
	}

	public Pagina<ResumenDocumento> listar(UsuarioId usuario, CuentaId cuenta, PeticionPagina peticion) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.lectura.pagina(usuario, cuenta, peticion));
	}

	public ResumenDocumento consultar(UsuarioId usuario, DocumentoId id) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.lectura.resumen(usuario, id))
				.orElseThrow(() -> new RecursoNoEncontrado("el documento", id));
	}

	private Documento propio(UsuarioId usuario, DocumentoId id) {
		return Propiedad.exigir(this.documentos.buscar(id), usuario, "el documento", id);
	}
}
