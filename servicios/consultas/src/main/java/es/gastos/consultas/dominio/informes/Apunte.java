package es.gastos.consultas.dominio.informes;

import es.gastos.kernel.dominio.Normalizador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un movimiento tal y como lo necesitan los informes: sus hechos, su cuenta y su clasificación.
 *
 * @param claveConcepto el concepto sin fechas ni tildes ({@code ClaveConcepto}), para agrupar
 * @param saldo         nulo si el banco no lo dio
 * @param orden         fila del extracto; desempata los movimientos de un mismo día
 */
public record Apunte(UUID id, LocalDate fecha, String concepto, String claveConcepto, BigDecimal importe, BigDecimal saldo,
		int orden, Referencia cuenta, Categoria categoria, List<Etiqueta> etiquetas) {

	public Apunte {
		concepto = concepto == null ? "" : concepto;
		claveConcepto = claveConcepto == null ? "" : claveConcepto;
		etiquetas = etiquetas == null ? List.of() : List.copyOf(etiquetas);
	}

	public record Referencia(UUID id, String nombre) {
	}

	public record Categoria(UUID id, String nombre, String color) {
	}

	/** {@code categoriaId} es la categoría de la etiqueta (la de su cascada), no la de la transacción. */
	public record Etiqueta(UUID id, String nombre, UUID categoriaId) {
	}

	public boolean esGasto() {
		return this.importe.signum() < 0;
	}

	/** El importe de un gasto en positivo. */
	public BigDecimal gasto() {
		return this.importe.negate();
	}

	/**
	 * Un movimiento es un Bizum si el concepto dice "bizum" o si lleva la etiqueta Bizum: el banco a
	 * veces deja en los recibidos solo el nombre de quien envía, y esos se reconocen por la etiqueta.
	 */
	public boolean esBizum() {
		return Normalizador.normalizar(this.concepto).contains("bizum")
				|| this.etiquetas.stream().anyMatch(e -> Normalizador.normalizarNombre(e.nombre()).equals("bizum"));
	}
}
