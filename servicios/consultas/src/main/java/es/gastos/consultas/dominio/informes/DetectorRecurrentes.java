package es.gastos.consultas.dominio.informes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detecta gastos recurrentes (suscripciones, recibos, cuotas) en todo el histórico. Port del back
 * antiguo con la misma semántica:
 * <ul>
 *   <li>Se agrupan los gastos por clave de concepto; hacen falta al menos 3.</li>
 *   <li>La <b>mediana</b> de los días entre apariciones fija la periodicidad (5–9 semanal, 12–18
 *   quincenal, 21–45 mensual, 75–105 trimestral, 330–400 anual); fuera de esos rangos no es
 *   recurrente. Se usa la mediana y no la media para que un mes saltado no lo estropee.</li>
 *   <li>El importe tiene que ser estable: el rango intercuartílico no supera el 30 % de la mediana
 *   (una gasolinera con 10, 60 y 110 € no es una cuota).</li>
 *   <li>Está activo si la última aparición es posterior a hoy menos dos periodos.</li>
 *   <li>Coste anual = importe mediano × 365 / intervalo. Se ordena por activos y por coste anual y se
 *   devuelven los 30 primeros.</li>
 * </ul>
 * "Hoy" llega como parámetro: el antiguo usaba {@code LocalDate.now()} y no se podía probar.
 */
public final class DetectorRecurrentes {

	public static final int MAXIMO = 30;

	private static final BigDecimal DISPERSION_MAXIMA = new BigDecimal("0.3");

	private DetectorRecurrentes() {
	}

	public enum Periodicidad {
		SEMANAL(5, 9), QUINCENAL(12, 18), MENSUAL(21, 45), TRIMESTRAL(75, 105), ANUAL(330, 400);

		private final int minimo;

		private final int maximo;

		Periodicidad(int minimo, int maximo) {
			this.minimo = minimo;
			this.maximo = maximo;
		}

		static Periodicidad de(long dias) {
			for (Periodicidad periodicidad : values()) {
				if (dias >= periodicidad.minimo && dias <= periodicidad.maximo) {
					return periodicidad;
				}
			}
			return null;
		}
	}

	/**
	 * @param importeMedio el importe mediano de cada aparición
	 * @param cadaDias     el intervalo mediano en días
	 */
	public record Recurrente(String concepto, Periodicidad periodicidad, long cadaDias, BigDecimal importeMedio, BigDecimal costeAnual,
			LocalDate ultimaFecha, boolean activo, int veces) {
	}

	public static List<Recurrente> detectar(List<Apunte> apuntes, Seleccion seleccion, LocalDate hoy) {
		Map<String, List<Apunte>> grupos = new LinkedHashMap<>();
		for (Apunte apunte : apuntes) {
			if (apunte.esGasto() && seleccion.incluye(apunte) && !apunte.claveConcepto().isBlank()) {
				grupos.computeIfAbsent(apunte.claveConcepto(), c -> new ArrayList<>()).add(apunte);
			}
		}

		List<Recurrente> recurrentes = new ArrayList<>();
		grupos.forEach((clave, grupo) -> {
			if (grupo.size() >= 3) {
				analizar(clave, grupo, hoy).ifPresent(recurrentes::add);
			}
		});
		return recurrentes.stream()
				.sorted(Comparator.comparing(Recurrente::activo).reversed().thenComparing(Comparator.comparing(Recurrente::costeAnual).reversed()))
				.limit(MAXIMO)
				.toList();
	}

	private static Optional<Recurrente> analizar(String clave, List<Apunte> grupo, LocalDate hoy) {
		List<LocalDate> fechas = grupo.stream().map(Apunte::fecha).sorted().toList();
		List<Long> intervalos = new ArrayList<>();
		for (int i = 1; i < fechas.size(); i++) {
			intervalos.add(ChronoUnit.DAYS.between(fechas.get(i - 1), fechas.get(i)));
		}
		long intervalo = intervalos.stream().sorted().toList().get(intervalos.size() / 2);
		Periodicidad periodicidad = Periodicidad.de(intervalo);
		if (periodicidad == null) {
			return Optional.empty();
		}

		List<BigDecimal> importes = grupo.stream().map(Apunte::gasto).sorted().toList();
		BigDecimal mediana = importes.get(importes.size() / 2);
		BigDecimal p25 = importes.get(importes.size() / 4);
		BigDecimal p75 = importes.get(importes.size() * 3 / 4);
		if (mediana.signum() <= 0 || p75.subtract(p25).compareTo(mediana.multiply(DISPERSION_MAXIMA)) > 0) {
			return Optional.empty();
		}

		LocalDate ultima = fechas.getLast();
		return Optional.of(new Recurrente(clave, periodicidad, intervalo, Informes.dinero(mediana),
				mediana.multiply(BigDecimal.valueOf(365)).divide(BigDecimal.valueOf(intervalo), 2, RoundingMode.HALF_UP), ultima,
				ultima.isAfter(hoy.minusDays(intervalo * 2)), grupo.size()));
	}
}
