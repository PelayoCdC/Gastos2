package es.gastos.movimientos.dominio.respaldo;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/**
 * Todos los movimientos del usuario se han sustituido por los de un respaldo. No es de ningún agregado:
 * afecta a todo lo del usuario, así que el "agregado" es el propio usuario y no lleva versión.
 */
public record MovimientosRestaurados(UsuarioId usuarioId, int cuentas, int documentos, int transacciones) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.usuarioId.valor();
	}

	@Override
	public long versionAgregado() {
		return 0;
	}
}
