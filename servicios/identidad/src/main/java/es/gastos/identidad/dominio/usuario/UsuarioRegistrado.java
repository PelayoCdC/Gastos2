package es.gastos.identidad.dominio.usuario;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/** Evento de dominio: un usuario nuevo se ha registrado. */
public record UsuarioRegistrado(UsuarioId usuarioId, long versionAgregado, Email email, Nombre nombre)
		implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.usuarioId.valor();
	}
}
