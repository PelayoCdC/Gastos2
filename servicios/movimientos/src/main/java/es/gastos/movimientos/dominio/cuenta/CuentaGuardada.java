package es.gastos.movimientos.dominio.cuenta;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/** La cuenta se ha creado o modificado; lleva su estado completo. */
public record CuentaGuardada(CuentaId cuentaId, UsuarioId usuarioId, long versionAgregado, NombreCuenta nombre,
		String banco, String descripcion) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.cuentaId.valor();
	}
}
