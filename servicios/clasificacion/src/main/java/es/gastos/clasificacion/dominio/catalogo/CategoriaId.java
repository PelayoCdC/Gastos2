package es.gastos.clasificacion.dominio.catalogo;

import java.util.Objects;
import java.util.UUID;

public record CategoriaId(UUID valor) {

	public CategoriaId {
		Objects.requireNonNull(valor, "El id de categoria es obligatorio");
	}

	@Override
	public String toString() {
		return this.valor.toString();
	}
}
