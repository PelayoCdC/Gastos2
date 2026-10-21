package es.gastos.movimientos.dominio.documento;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;

import java.util.UUID;

/** El documento y todas sus transacciones han pasado a otra cuenta. */
public record DocumentoReasignado(DocumentoId documentoId, UsuarioId usuarioId, long versionAgregado,
		CuentaId cuentaAnterior, CuentaId cuentaNueva) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.documentoId.valor();
	}
}
