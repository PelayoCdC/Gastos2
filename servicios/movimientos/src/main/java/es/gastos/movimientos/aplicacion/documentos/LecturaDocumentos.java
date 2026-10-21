package es.gastos.movimientos.aplicacion.documentos;

import es.gastos.kernel.aplicacion.Pagina;
import es.gastos.kernel.aplicacion.PeticionPagina;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;

import java.util.Optional;

/** Puerto de lectura de documentos (listado paginado, más recientes primero). */
public interface LecturaDocumentos {

	Pagina<ResumenDocumento> pagina(UsuarioId usuario, CuentaId cuenta, PeticionPagina peticion);

	Optional<ResumenDocumento> resumen(UsuarioId usuario, DocumentoId documento);
}
