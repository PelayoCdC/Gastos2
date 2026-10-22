package es.gastos.consultas.aplicacion.consulta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Una transacción tal y como la ve el usuario: los hechos de movimientos, los nombres de su cuenta y
 * su documento, y su clasificación. Es la fila de la rejilla y también el detalle.
 */
public record FilaTransaccion(UUID id, LocalDate fechaContable, LocalDate fechaValor, String concepto, String nota,
		BigDecimal importe, BigDecimal saldo, int orden, Referencia cuenta, Referencia documento, Categoria categoria,
		List<Referencia> etiquetas) {

	public FilaTransaccion {
		etiquetas = etiquetas == null ? List.of() : List.copyOf(etiquetas);
	}

	public FilaTransaccion conEtiquetas(List<Referencia> lista) {
		return new FilaTransaccion(this.id, this.fechaContable, this.fechaValor, this.concepto, this.nota, this.importe, this.saldo,
				this.orden, this.cuenta, this.documento, this.categoria, lista);
	}

	public record Referencia(UUID id, String nombre) {
	}

	public record Categoria(UUID id, String nombre, String color) {
	}
}
