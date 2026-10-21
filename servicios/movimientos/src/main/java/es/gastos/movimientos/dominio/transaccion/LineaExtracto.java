package es.gastos.movimientos.dominio.transaccion;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Una fila de extracto ya interpretada (la capa anticorrupción de importacion ha resuelto columnas,
 * formatos de fecha y separadores decimales): el lenguaje limpio en el que movimientos recibe los
 * hechos del banco.
 *
 * @param fila número de fila en la hoja, empezando en 1; se conserva como {@code orden} de la transacción
 */
public record LineaExtracto(int fila, LocalDate fechaContable, LocalDate fechaValor, Concepto concepto, Importe importe,
		Importe saldo) {

	public LineaExtracto {
		if (fila < 1) {
			throw new ExcepcionDominio("LINEA_INVALIDA", "El numero de fila empieza en 1");
		}
		Objects.requireNonNull(fechaContable, "La fecha contable es obligatoria");
		Objects.requireNonNull(concepto);
		Objects.requireNonNull(importe, "El importe es obligatorio");
	}

	public ClaveDuplicado clave() {
		return ClaveDuplicado.de(this.fechaContable, this.importe, this.concepto);
	}
}
