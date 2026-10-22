package es.gastos.importacion.dominio.formato;

import es.gastos.kernel.dominio.UsuarioId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de formatos de banco. */
public interface FormatoRepositorio {

	void guardar(FormatoBanco formato);

	Optional<FormatoBanco> buscar(FormatoId id);

	/** Los del usuario, por nombre. */
	List<FormatoBanco> delUsuario(UsuarioId usuario);

	boolean existeNombre(UsuarioId usuario, String nombreNormalizado, FormatoId excepto);

	void eliminar(FormatoBanco formato);

	/** Borra todos los formatos del usuario (restaurar un respaldo). */
	void eliminarDelUsuario(UsuarioId usuario);

	/** Si alguno de esos ids ya es de otro usuario. */
	boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<FormatoId> ids);
}
