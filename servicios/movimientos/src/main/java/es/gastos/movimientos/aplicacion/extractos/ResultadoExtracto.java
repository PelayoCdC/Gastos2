package es.gastos.movimientos.aplicacion.extractos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resultado de registrar un extracto: cuántas filas se importaron y cuáles se omitieron por estar ya
 * registradas. Se guarda en el documento para responder igual a un reintento.
 */
public record ResultadoExtracto(UUID documentoId, String nombreDocumento, String nombreArchivo, int importadas,
		List<Duplicada> duplicados) {

	public ResultadoExtracto {
		duplicados = List.copyOf(duplicados);
	}

	/** Fila omitida por duplicada, identificada por su número de fila en el fichero. */
	public record Duplicada(int fila, LocalDate fechaContable, String concepto, BigDecimal importe) {
	}
}
