package es.gastos.movimientos.dominio.cuenta;

import java.util.Objects;
import java.util.UUID;

public record CuentaId(UUID valor) {

	public CuentaId {
		Objects.requireNonNull(valor, "El id de cuenta es obligatorio");
	}

	@Override
	public String toString() {
		return this.valor.toString();
	}
}
