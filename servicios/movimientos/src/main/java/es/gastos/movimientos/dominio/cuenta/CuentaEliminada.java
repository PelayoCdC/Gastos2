package es.gastos.movimientos.dominio.cuenta;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

public record CuentaEliminada(CuentaId cuentaId, UsuarioId usuarioId, long versionAgregado) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.cuentaId.valor();
	}
}
