package es.gastos.movimientos.dominio.transaccion;

import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de transacciones, con las operaciones por conjuntos que piden los documentos. */
public interface TransaccionRepositorio {

	void guardar(Transaccion transaccion);

	Optional<Transaccion> buscar(TransaccionId id);

	void eliminar(Transaccion transaccion);

	/** Número de transacciones (de cualquier origen) asociadas a la cuenta. */
	long contarEnCuenta(CuentaId cuenta);

	/** Transacciones de la cuenta que no vienen de un documento (las de documento se borran con él). */
	List<Transaccion> sinDocumentoDeCuenta(CuentaId cuenta);

	/** Borra todas las transacciones del documento; devuelve cuántas. */
	int eliminarDeDocumento(DocumentoId documento);

	/** Mueve todas las transacciones del documento a la cuenta (incrementa su versión); devuelve cuántas. */
	int moverDocumentoACuenta(DocumentoId documento, CuentaId cuenta);
}
