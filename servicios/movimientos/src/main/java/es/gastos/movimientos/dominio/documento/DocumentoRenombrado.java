package es.gastos.movimientos.dominio.documento;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

public record DocumentoRenombrado(DocumentoId documentoId, UsuarioId usuarioId, long versionAgregado, NombreDocumento nombre)
		implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.documentoId.valor();
	}
}
