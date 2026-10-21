package es.gastos.movimientos.dominio.transaccion;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/** Se han corregido los hechos editables de la transacción; lleva su estado completo. */
public record TransaccionCorregida(UsuarioId usuarioId, long versionAgregado, Transaccion.Datos datos) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.datos.id().valor();
	}
}
