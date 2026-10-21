package es.gastos.importacion.dominio.formato;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

public record FormatoBancoGuardado(FormatoId formatoId, UsuarioId usuarioId, long versionAgregado, NombreFormato nombre,
		int filaInicio, MapeoColumnas columnas, FormatoFecha formatoFecha) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.formatoId.valor();
	}
}
