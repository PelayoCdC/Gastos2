package es.gastos.movimientos.dominio.transaccion;

import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Puerto de persistencia de transacciones, con las operaciones por conjuntos que piden los documentos. */
public interface TransaccionRepositorio {

	void guardar(Transaccion transaccion);

	Optional<Transaccion> buscar(TransaccionId id);

	void eliminar(Transaccion transaccion);

	/** Borra todas las transacciones del usuario (restaurar un respaldo). */
	void eliminarDelUsuario(UsuarioId usuario);

	/** Si alguno de esos ids ya es de otro usuario (un respaldo de otra cuenta no puede reutilizarlos). */
	boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids);

	/** Número de transacciones (de cualquier origen) asociadas a la cuenta. */
	long contarEnCuenta(CuentaId cuenta);

	/** Transacciones de la cuenta que no vienen de un documento (las de documento se borran con él). */
	List<Transaccion> sinDocumentoDeCuenta(CuentaId cuenta);

	/** Borra todas las transacciones del documento; devuelve cuántas. */
	int eliminarDeDocumento(DocumentoId documento);

	/** Mueve todas las transacciones del documento a la cuenta (incrementa su versión); devuelve cuántas. */
	int moverDocumentoACuenta(DocumentoId documento, CuentaId cuenta);

	/** Inserta transacciones nuevas por lotes (importación de extractos). */
	void insertarTodas(List<Transaccion> nuevas);

	/**
	 * Cuántas transacciones del usuario tienen cada una de las claves, dentro del ámbito de
	 * deduplicación de una importación: la cuenta destino más las transacciones sin cuenta.
	 */
	Map<ClaveDuplicado, Long> contarClaves(UsuarioId usuario, CuentaId cuenta, Set<ClaveDuplicado> claves);

	/** Transacciones del usuario con esos ids (las ajenas o inexistentes simplemente no aparecen). */
	List<Transaccion> buscarVarias(UsuarioId usuario, Collection<TransaccionId> ids);
}
