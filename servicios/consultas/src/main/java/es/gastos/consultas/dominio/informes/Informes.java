package es.gastos.consultas.dominio.informes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Informes de agregación sobre los apuntes de un usuario (ver el paquete para las convenciones). */
public final class Informes {

	private static final Comparator<String> NOMBRES = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

	private Informes() {
	}

	public record ResumenMes(int anio, int mes, BigDecimal gastos, BigDecimal ingresos, BigDecimal balance) {
	}

	public record SerieSaldo(Apunte.Referencia cuenta, List<PuntoSaldo> puntos) {
	}

	public record PuntoSaldo(LocalDate fecha, BigDecimal saldo) {
	}

	/** {@code categoria} nula es el grupo "Sin categoría". */
	public record TotalCategoria(Apunte.Categoria categoria, BigDecimal total, int numeroTransacciones) {
	}

	public record TotalEtiqueta(Apunte.Referencia etiqueta, BigDecimal total, int numeroTransacciones) {
	}

	public record GastoDia(LocalDate fecha, BigDecimal gasto) {
	}

	public record GastoMesCategoria(int anio, int mes, Apunte.Categoria categoria, BigDecimal total) {
	}

	public record TopConcepto(String concepto, BigDecimal total, int numero, BigDecimal media) {
	}

	/** Gastos (en positivo), ingresos y balance de cada mes con movimientos, en orden cronológico. */
	public static List<ResumenMes> resumenMensual(List<Apunte> apuntes, Seleccion seleccion) {
		Map<YearMonth, BigDecimal[]> meses = new TreeMap<>();
		for (Apunte apunte : apuntes) {
			if (!seleccion.incluye(apunte)) {
				continue;
			}
			BigDecimal[] totales = meses.computeIfAbsent(YearMonth.from(apunte.fecha()), m -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
			if (apunte.esGasto()) {
				totales[0] = totales[0].add(apunte.gasto());
			} else {
				totales[1] = totales[1].add(apunte.importe());
			}
		}
		return meses.entrySet().stream()
				.map(e -> new ResumenMes(e.getKey().getYear(), e.getKey().getMonthValue(), dinero(e.getValue()[0]), dinero(e.getValue()[1]),
						dinero(e.getValue()[1].subtract(e.getValue()[0]))))
				.toList();
	}

	/**
	 * Por cuenta, el saldo al final de cada día con movimientos: el del último movimiento del día (el de
	 * mayor {@code orden}), que es como lo presenta el extracto. Los movimientos sin saldo no cuentan y
	 * las cuentas se identifican por id (el antiguo agrupaba por nombre y mezclaba cuentas homónimas).
	 */
	public static List<SerieSaldo> evolucionSaldo(List<Apunte> apuntes) {
		Map<UUID, Apunte.Referencia> cuentas = new LinkedHashMap<>();
		Map<UUID, Map<LocalDate, BigDecimal>> saldos = new LinkedHashMap<>();
		apuntes.stream()
				.filter(a -> a.cuenta() != null && a.saldo() != null)
				.sorted(Comparator.comparing(Apunte::fecha).thenComparingInt(Apunte::orden))
				.forEach(a -> {
					cuentas.putIfAbsent(a.cuenta().id(), a.cuenta());
					saldos.computeIfAbsent(a.cuenta().id(), id -> new LinkedHashMap<>()).put(a.fecha(), dinero(a.saldo()));
				});
		List<SerieSaldo> series = new ArrayList<>();
		saldos.forEach((cuenta, porDia) -> series.add(new SerieSaldo(cuentas.get(cuenta),
				porDia.entrySet().stream().map(d -> new PuntoSaldo(d.getKey(), d.getValue())).toList())));
		return series;
	}

	/** Gasto total por categoría, de mayor a menor, con un grupo para lo que no tiene categoría. */
	public static List<TotalCategoria> gastosPorCategoria(List<Apunte> apuntes) {
		Map<UUID, Apunte.Categoria> categorias = new LinkedHashMap<>();
		Map<UUID, Acumulado> totales = new LinkedHashMap<>();
		for (Apunte apunte : apuntes) {
			if (!apunte.esGasto()) {
				continue;
			}
			UUID clave = apunte.categoria() == null ? null : apunte.categoria().id();
			if (apunte.categoria() != null) {
				categorias.putIfAbsent(clave, apunte.categoria());
			}
			totales.computeIfAbsent(clave, c -> new Acumulado()).sumar(apunte.gasto());
		}
		return totales.entrySet().stream()
				.map(e -> new TotalCategoria(e.getKey() == null ? null : categorias.get(e.getKey()), dinero(e.getValue().total),
						e.getValue().numero))
				.sorted(Comparator.comparing(TotalCategoria::total).reversed())
				.toList();
	}

	/**
	 * Gasto total por etiqueta, de mayor a menor. Un movimiento con varias etiquetas suma en cada una.
	 * Con {@code categoriasDeLaEtiqueta}, solo las etiquetas que pertenecen (en cascada) a esas categorías.
	 */
	public static List<TotalEtiqueta> gastosPorEtiqueta(List<Apunte> apuntes, Set<UUID> categoriasDeLaEtiqueta) {
		Map<UUID, Apunte.Referencia> etiquetas = new LinkedHashMap<>();
		Map<UUID, Acumulado> totales = new LinkedHashMap<>();
		for (Apunte apunte : apuntes) {
			if (!apunte.esGasto()) {
				continue;
			}
			for (Apunte.Etiqueta etiqueta : apunte.etiquetas()) {
				if (!categoriasDeLaEtiqueta.isEmpty() && !categoriasDeLaEtiqueta.contains(etiqueta.categoriaId())) {
					continue;
				}
				etiquetas.putIfAbsent(etiqueta.id(), new Apunte.Referencia(etiqueta.id(), etiqueta.nombre()));
				totales.computeIfAbsent(etiqueta.id(), e -> new Acumulado()).sumar(apunte.gasto());
			}
		}
		return totales.entrySet().stream()
				.map(e -> new TotalEtiqueta(etiquetas.get(e.getKey()), dinero(e.getValue().total), e.getValue().numero))
				.sorted(Comparator.comparing(TotalEtiqueta::total).reversed())
				.toList();
	}

	/** Gasto de cada día con gastos, en orden cronológico (para el calendario de calor). */
	public static List<GastoDia> gastoDiario(List<Apunte> apuntes, Seleccion seleccion) {
		Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
		for (Apunte apunte : apuntes) {
			if (apunte.esGasto() && seleccion.incluye(apunte)) {
				porDia.merge(apunte.fecha(), apunte.gasto(), BigDecimal::add);
			}
		}
		return porDia.entrySet().stream().map(e -> new GastoDia(e.getKey(), dinero(e.getValue()))).toList();
	}

	/** Composición del gasto de cada mes por categoría (gráfico apilado), por mes y nombre de categoría. */
	public static List<GastoMesCategoria> gastosMensualesPorCategoria(List<Apunte> apuntes) {
		record Clave(YearMonth mes, UUID categoria) {
		}
		Map<Clave, Apunte.Categoria> categorias = new LinkedHashMap<>();
		Map<Clave, BigDecimal> totales = new LinkedHashMap<>();
		for (Apunte apunte : apuntes) {
			if (!apunte.esGasto()) {
				continue;
			}
			Clave clave = new Clave(YearMonth.from(apunte.fecha()), apunte.categoria() == null ? null : apunte.categoria().id());
			categorias.putIfAbsent(clave, apunte.categoria());
			totales.merge(clave, apunte.gasto(), BigDecimal::add);
		}
		return totales.entrySet().stream()
				.map(e -> new GastoMesCategoria(e.getKey().mes().getYear(), e.getKey().mes().getMonthValue(), categorias.get(e.getKey()),
						dinero(e.getValue())))
				.sorted(Comparator.comparingInt(GastoMesCategoria::anio).thenComparingInt(GastoMesCategoria::mes)
						.thenComparing(g -> g.categoria() == null ? null : g.categoria().nombre(), NOMBRES))
				.toList();
	}

	/**
	 * Los conceptos en los que más se gasta, agrupando por la clave sin fechas ("GOGALIA 04 02 2024" y
	 * "GOGALIA 15 10 2023" son el mismo comercio). Los conceptos sin texto significativo no cuentan.
	 */
	public static List<TopConcepto> topConceptos(List<Apunte> apuntes, Seleccion seleccion, int limite) {
		Map<String, Acumulado> grupos = new LinkedHashMap<>();
		for (Apunte apunte : apuntes) {
			if (apunte.esGasto() && seleccion.incluye(apunte) && !apunte.claveConcepto().isBlank()) {
				grupos.computeIfAbsent(apunte.claveConcepto(), c -> new Acumulado()).sumar(apunte.gasto());
			}
		}
		return grupos.entrySet().stream()
				.map(e -> new TopConcepto(e.getKey(), dinero(e.getValue().total), e.getValue().numero,
						e.getValue().total.divide(BigDecimal.valueOf(e.getValue().numero), 2, RoundingMode.HALF_UP)))
				.sorted(Comparator.comparing(TopConcepto::total).reversed())
				.limit(limite)
				.toList();
	}

	static BigDecimal dinero(BigDecimal valor) {
		return Objects.requireNonNull(valor).setScale(2, RoundingMode.HALF_UP);
	}

	private static final class Acumulado {

		private BigDecimal total = BigDecimal.ZERO;

		private int numero;

		void sumar(BigDecimal importe) {
			this.total = this.total.add(importe);
			this.numero++;
		}
	}
}
