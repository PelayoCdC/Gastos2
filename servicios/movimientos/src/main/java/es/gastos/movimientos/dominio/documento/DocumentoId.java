package es.gastos.movimientos.dominio.documento;

import java.util.Objects;
import java.util.UUID;

public record DocumentoId(UUID valor) {

	public DocumentoId {
		Objects.requireNonNull(valor, "El id de documento es obligatorio");
	}

	@Override
	public String toString() {
		return this.valor.toString();
	}
}
