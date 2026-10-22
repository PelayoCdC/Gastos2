package es.gastos.movimientos.dominio.documento;

import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de documentos. */
public interface DocumentoRepositorio {

	void guardar(Documento documento);

	Optional<Documento> buscar(DocumentoId id);

	List<Documento> deCuenta(CuentaId cuenta);

	void eliminar(Documento documento);

	/** Borra todos los documentos del usuario (restaurar un respaldo); antes hay que borrar sus transacciones. */
	void eliminarDelUsuario(UsuarioId usuario);

	/** Si alguno de esos ids ya es de otro usuario (un respaldo de otra cuenta no puede reutilizarlos). */
	boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids);
}
