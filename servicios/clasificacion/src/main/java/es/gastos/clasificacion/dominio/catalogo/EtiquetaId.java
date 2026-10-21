package es.gastos.clasificacion.dominio.catalogo;

import java.util.Objects;
import java.util.UUID;

public record EtiquetaId(UUID valor) {

	public EtiquetaId {
		Objects.requireNonNull(valor, "El id de etiqueta es obligatorio");
	}

	@Override
	public String toString() {
		return this.valor.toString();
	}
}
