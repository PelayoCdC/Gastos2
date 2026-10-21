package es.gastos.movimientos.dominio.transaccion;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.documento.DocumentoId;

import java.util.UUID;

public record TransaccionEliminada(TransaccionId transaccionId, UsuarioId usuarioId, long versionAgregado,
		DocumentoId documentoId) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.transaccionId.valor();
	}
}
