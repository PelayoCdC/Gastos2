package es.gastos.importacion.dominio.respaldo;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/** Los formatos del usuario se han sustituido por los de un respaldo; el "agregado" es el usuario. */
public record FormatosRestaurados(UsuarioId usuarioId, int formatos) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.usuarioId.valor();
	}

	@Override
	public long versionAgregado() {
		return 0;
	}
}
