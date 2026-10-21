package es.gastos.importacion.dominio.extracto;

import es.gastos.kernel.dominio.Importe;

import java.time.LocalDate;
import java.util.List;

/** Resultado de interpretar un extracto: las líneas válidas y los errores, ambos por número de fila. */
public record Interpretacion(List<Linea> lineas, List<ErrorFila> errores) {

	public Interpretacion {
		lineas = List.copyOf(lineas);
		errores = List.copyOf(errores);
	}

	/** Movimiento listo para registrar. {@code fila} cuenta desde 1, como la ve el usuario. */
	public record Linea(int fila, LocalDate fechaContable, LocalDate fechaValor, String concepto, Importe importe, Importe saldo) {
	}

	/** Fila descartada y por qué (el mensaje se muestra tal cual al usuario). */
	public record ErrorFila(int fila, String mensaje) {
	}
}
