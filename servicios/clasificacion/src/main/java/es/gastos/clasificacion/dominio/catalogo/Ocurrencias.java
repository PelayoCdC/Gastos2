package es.gastos.clasificacion.dominio.catalogo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista ordenada de ocurrencias de una categoría o etiqueta, sin repetidas (dos ocurrencias que solo
 * difieren en mayúsculas o tildes son la misma regla; se conserva la primera).
 */
public record Ocurrencias(List<Ocurrencia> lista) {

	public static final Ocurrencias NINGUNA = new Ocurrencias(List.of());

	public Ocurrencias {
		Map<String, Ocurrencia> unicas = new LinkedHashMap<>();
		for (Ocurrencia ocurrencia : lista) {
			unicas.putIfAbsent(ocurrencia.normalizada(), ocurrencia);
		}
		lista = List.copyOf(unicas.values());
	}

	/** Desde textos de la API: los nulos o vacíos se descartan (una lista con huecos no es un error). */
	public static Ocurrencias de(List<String> textos) {
		if (textos == null) {
			return NINGUNA;
		}
		List<Ocurrencia> ocurrencias = new ArrayList<>();
		for (String texto : textos) {
			if (texto != null && !texto.isBlank()) {
				ocurrencias.add(new Ocurrencia(texto));
			}
		}
		return new Ocurrencias(ocurrencias);
	}

	public boolean coincideCon(String conceptoNormalizado) {
		return this.lista.stream().anyMatch(ocurrencia -> ocurrencia.apareceEn(conceptoNormalizado));
	}

	public boolean vacia() {
		return this.lista.isEmpty();
	}

	public List<String> textos() {
		return this.lista.stream().map(Ocurrencia::texto).toList();
	}
}
