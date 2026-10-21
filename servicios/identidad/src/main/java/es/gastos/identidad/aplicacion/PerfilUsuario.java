package es.gastos.identidad.aplicacion;

import es.gastos.identidad.dominio.usuario.Usuario;

import java.util.UUID;

/** Datos públicos de un usuario. */
public record PerfilUsuario(UUID id, String nombre, String email) {

	static PerfilUsuario de(Usuario usuario) {
		return new PerfilUsuario(usuario.id().valor(), usuario.nombre().valor(), usuario.email().valor());
	}
}
