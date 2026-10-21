package es.gastos.movimientos.dominio.documento;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/** El documento y todas sus transacciones se han borrado. */
public record DocumentoEliminado(DocumentoId documentoId, UsuarioId usuarioId, long versionAgregado) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.documentoId.valor();
	}
}
