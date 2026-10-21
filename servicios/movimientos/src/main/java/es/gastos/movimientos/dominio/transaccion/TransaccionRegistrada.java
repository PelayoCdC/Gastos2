package es.gastos.movimientos.dominio.transaccion;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/** Se ha registrado la transacción; lleva su estado completo. */
public record TransaccionRegistrada(UsuarioId usuarioId, long versionAgregado, Transaccion.Datos datos) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.datos.id().valor();
	}
}
