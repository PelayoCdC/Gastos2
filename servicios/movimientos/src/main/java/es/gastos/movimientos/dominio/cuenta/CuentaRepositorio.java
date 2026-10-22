package es.gastos.movimientos.dominio.cuenta;

import es.gastos.kernel.dominio.UsuarioId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de cuentas. */
public interface CuentaRepositorio {

	void guardar(Cuenta cuenta);

	Optional<Cuenta> buscar(CuentaId id);

	/**
	 * Busca la cuenta bloqueando su fila hasta el final de la transacción: serializa las
	 * importaciones simultáneas en la misma cuenta para que la detección de duplicados sea correcta.
	 */
	Optional<Cuenta> buscarParaActualizar(CuentaId id);

	/** ¿Tiene el usuario otra cuenta (distinta de {@code excepto}) con ese nombre normalizado? */
	boolean existeNombre(UsuarioId usuario, String nombreNormalizado, CuentaId excepto);

	void eliminar(Cuenta cuenta);

	/** Borra todas las cuentas del usuario (restaurar un respaldo); antes hay que borrar sus documentos y transacciones. */
	void eliminarDelUsuario(UsuarioId usuario);

	/** Si alguno de esos ids ya es de otro usuario (un respaldo de otra cuenta no puede reutilizarlos). */
	boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids);
}
