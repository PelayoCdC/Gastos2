package es.gastos.identidad.dominio.usuario;

import es.gastos.kernel.dominio.UsuarioId;

import java.util.Optional;

/** Puerto de persistencia de usuarios (lo implementa la infraestructura). */
public interface UsuarioRepositorio {

	/** Inserta o actualiza; lanza {@code Conflicto} si el email ya existe o si hubo un cambio concurrente. */
	void guardar(Usuario usuario);

	Optional<Usuario> buscarPorId(UsuarioId id);

	Optional<Usuario> buscarPorEmail(Email email);

	boolean existeEmail(Email email);
}
