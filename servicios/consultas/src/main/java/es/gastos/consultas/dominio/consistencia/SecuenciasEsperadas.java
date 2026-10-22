package es.gastos.consultas.dominio.consistencia;

import es.gastos.kernel.dominio.ExcepcionDominio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Secuencias por contexto que la proyección debe haber alcanzado, tal y como llegan en
 * {@code Gastos-Esperar: movimientos=18342,clasificacion=977}.
 *
 * <p>Una cabecera mal formada es un error del cliente (400) y no se ignora: si se ignorara, el
 * front creería estar leyendo datos al día cuando no lo están.
 */
public record SecuenciasEsperadas(Map<String, Long> porContexto) {

	public static final SecuenciasEsperadas NINGUNA = new SecuenciasEsperadas(Map.of());

	private static final Pattern PARTE = Pattern.compile("([a-z]+)=(\\d{1,18})");

	public SecuenciasEsperadas {
		porContexto = Map.copyOf(porContexto);
	}

	public static SecuenciasEsperadas de(String cabecera) {
		if (cabecera == null || cabecera.isBlank()) {
			return NINGUNA;
		}
		Map<String, Long> secuencias = new LinkedHashMap<>();
		for (String parte : cabecera.split(",")) {
			var encaje = PARTE.matcher(parte.strip());
			if (!encaje.matches()) {
				throw new ExcepcionDominio("CABECERA_INVALIDA",
						"Gastos-Esperar debe tener la forma contexto=secuencia[,contexto=secuencia]: '" + cabecera + "'");
			}
			secuencias.merge(encaje.group(1), Long.parseLong(encaje.group(2)), Math::max);
		}
		return new SecuenciasEsperadas(secuencias);
	}

	public boolean vacia() {
		return this.porContexto.isEmpty();
	}

	/** Solo las de los contextos indicados (las de un contexto que no se proyecta nunca se alcanzarían). */
	public SecuenciasEsperadas soloDe(Predicate<String> contextos) {
		Map<String, Long> filtradas = new LinkedHashMap<>();
		this.porContexto.forEach((contexto, secuencia) -> {
			if (contextos.test(contexto)) {
				filtradas.put(contexto, secuencia);
			}
		});
		return new SecuenciasEsperadas(filtradas);
	}

	/** Si las posiciones de la proyección llegan a todas las esperadas (un contexto sin posición está en 0). */
	public boolean alcanzadasPor(Map<String, Long> posiciones) {
		return this.porContexto.entrySet().stream()
				.allMatch(esperada -> posiciones.getOrDefault(esperada.getKey(), 0L) >= esperada.getValue());
	}
}
