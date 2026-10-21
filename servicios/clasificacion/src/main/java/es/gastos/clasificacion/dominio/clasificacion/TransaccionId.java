package es.gastos.clasificacion.dominio.clasificacion;

import java.util.Objects;
import java.util.UUID;

/** Id de la transacción en movimientos (la clasificación usa el mismo id: es 1 a 1). */
public record TransaccionId(UUID valor) {

	public TransaccionId {
		Objects.requireNonNull(valor, "El id de transaccion es obligatorio");
	}

	@Override
	public String toString() {
		return this.valor.toString();
	}
}
