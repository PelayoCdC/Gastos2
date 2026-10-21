package es.gastos.movimientos.dominio.transaccion;

import java.util.Objects;
import java.util.UUID;

public record TransaccionId(UUID valor) {

	public TransaccionId {
		Objects.requireNonNull(valor, "El id de transaccion es obligatorio");
	}

	@Override
	public String toString() {
		return this.valor.toString();
	}
}
