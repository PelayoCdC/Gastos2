package es.gastos.movimientos.dominio.transaccion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de dominio que separa las filas nuevas de un extracto de las que ya estaban registradas
 * (los extractos se solapan: el de octubre suele traer los últimos días de septiembre).
 *
 * <p>Compara por <b>conteo</b> de claves, no por presencia (docs/adr/0015): si una clave existe
 * {@code n} veces, las {@code n} primeras filas del fichero con esa clave son duplicadas y el resto
 * son nuevas. Así dos movimientos reales idénticos en el mismo extracto (dos cafés iguales el mismo
 * día) se importan los dos, y reimportar ese extracto los marca a los dos como duplicados. El back
 * antiguo usaba un conjunto y se quedaba con uno solo.
 */
public final class DetectorDuplicados {

	/** Filas en el orden del fichero, separadas en nuevas y duplicadas. */
	public record Separacion(List<LineaExtracto> nuevas, List<LineaExtracto> duplicadas) {
	}

	private DetectorDuplicados() {
	}

	/**
	 * @param lineas     filas del extracto en su orden
	 * @param existentes cuántas transacciones ya registradas (en el ámbito de la importación) tienen
	 *                   cada clave; las claves ausentes cuentan como 0
	 */
	public static Separacion separar(List<LineaExtracto> lineas, Map<ClaveDuplicado, Long> existentes) {
		Map<ClaveDuplicado, Long> pendientes = new HashMap<>(existentes);
		List<LineaExtracto> nuevas = new ArrayList<>();
		List<LineaExtracto> duplicadas = new ArrayList<>();

		for (LineaExtracto linea : lineas) {
			ClaveDuplicado clave = linea.clave();
			long quedan = pendientes.getOrDefault(clave, 0L);
			if (quedan > 0) {
				duplicadas.add(linea);
				pendientes.put(clave, quedan - 1);
			} else {
				nuevas.add(linea);
			}
		}

		return new Separacion(List.copyOf(nuevas), List.copyOf(duplicadas));
	}
}
