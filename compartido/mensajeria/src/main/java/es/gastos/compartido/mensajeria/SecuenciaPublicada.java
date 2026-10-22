package es.gastos.compartido.mensajeria;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Últimas secuencias de outbox escritas por el hilo actual, por contexto. El filtro web las devuelve
 * en la cabecera {@code Gastos-Secuencia} para que el cliente pueda "leer lo que acaba de escribir"
 * en consultas (docs/adr/0011).
 *
 * <p>Normalmente solo hay una, la del propio servicio. Un servicio que provoca escrituras en otro
 * (importacion en movimientos) registra también la secuencia que este le devuelve, para que el
 * cliente espere a los eventos que de verdad importan.
 */
public final class SecuenciaPublicada {

	public static final String CABECERA = "Gastos-Secuencia";

	private static final ThreadLocal<Map<String, Long>> ULTIMAS = ThreadLocal.withInitial(LinkedHashMap::new);

	private SecuenciaPublicada() {
	}

	/** Anota una secuencia de un contexto; si ya había otra, se queda la mayor. */
	public static void registrar(String contexto, long secuencia) {
		ULTIMAS.get().merge(contexto, secuencia, Math::max);
	}

	/** Anota todas las secuencias de una cabecera {@code Gastos-Secuencia} recibida de otro servicio. */
	public static void registrarCabecera(String cabecera) {
		if (cabecera == null || cabecera.isBlank()) {
			return;
		}
		for (String parte : cabecera.split(",")) {
			String[] contextoYSecuencia = parte.strip().split("=");
			if (contextoYSecuencia.length == 2) {
				try {
					registrar(contextoYSecuencia[0].strip(), Long.parseLong(contextoYSecuencia[1].strip()));
				} catch (NumberFormatException ignorada) {
					// Una cabecera ajena mal formada no debe romper la respuesta propia
				}
			}
		}
	}

	/** Devuelve las secuencias anotadas en este hilo como valor de cabecera ("a=1,b=2") y las olvida. */
	public static String consumir() {
		Map<String, Long> ultimas = ULTIMAS.get();
		String cabecera = ultimas.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
		ULTIMAS.remove();
		return cabecera;
	}
}
