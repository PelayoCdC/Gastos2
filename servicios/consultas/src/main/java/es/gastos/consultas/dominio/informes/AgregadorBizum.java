package es.gastos.consultas.dominio.informes;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bizums agrupados por contacto (port del back antiguo). Cuenta como Bizum lo que dice "bizum" en el
 * concepto o lleva la etiqueta Bizum ({@link Apunte#esBizum()}). El contacto es lo que va tras el
 * primer {@code :} ("BIZUM: Cena familia" → "cena familia") o el concepto entero (los recibidos que el
 * banco deja con solo el nombre del emisor), en minúsculas. Se ordena por número de veces y se
 * devuelven los 20 primeros.
 */
public final class AgregadorBizum {

	public static final int MAXIMO = 20;

	static final String SIN_DETALLE = "(sin detalle)";

	private AgregadorBizum() {
	}

	public record ContactoBizum(String contacto, BigDecimal enviado, BigDecimal recibido, BigDecimal neto, int veces) {
	}

	public static List<ContactoBizum> agrupar(List<Apunte> apuntes, Seleccion seleccion) {
		Map<String, BigDecimal[]> contactos = new LinkedHashMap<>();
		Map<String, Integer> veces = new LinkedHashMap<>();
		for (Apunte apunte : apuntes) {
			if (!seleccion.incluye(apunte) || !apunte.esBizum()) {
				continue;
			}
			String contacto = contacto(apunte.concepto());
			BigDecimal[] totales = contactos.computeIfAbsent(contacto, c -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
			if (apunte.esGasto()) {
				totales[0] = totales[0].add(apunte.gasto());
			} else {
				totales[1] = totales[1].add(apunte.importe());
			}
			veces.merge(contacto, 1, Integer::sum);
		}
		return contactos.entrySet().stream()
				.map(e -> new ContactoBizum(e.getKey(), Informes.dinero(e.getValue()[0]), Informes.dinero(e.getValue()[1]),
						Informes.dinero(e.getValue()[1].subtract(e.getValue()[0])), veces.get(e.getKey())))
				.sorted(Comparator.comparingInt(ContactoBizum::veces).reversed())
				.limit(MAXIMO)
				.toList();
	}

	static String contacto(String concepto) {
		int separador = concepto.indexOf(':');
		String contacto = (separador >= 0 ? concepto.substring(separador + 1) : concepto).strip();
		return contacto.isEmpty() ? SIN_DETALLE : contacto.toLowerCase(Locale.ROOT);
	}
}
