package es.gastos.movimientos.dominio.documento;

import es.gastos.movimientos.dominio.cuenta.CuentaId;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de documentos. */
public interface DocumentoRepositorio {

	void guardar(Documento documento);

	Optional<Documento> buscar(DocumentoId id);

	List<Documento> deCuenta(CuentaId cuenta);

	void eliminar(Documento documento);
}
