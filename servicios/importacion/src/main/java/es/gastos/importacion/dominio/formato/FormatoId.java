package es.gastos.importacion.dominio.formato;

import java.util.Objects;
import java.util.UUID;

public record FormatoId(UUID valor) {

	public FormatoId {
		Objects.requireNonNull(valor, "El id de formato es obligatorio");
	}

	@Override
	public String toString() {
		return this.valor.toString();
	}
}
