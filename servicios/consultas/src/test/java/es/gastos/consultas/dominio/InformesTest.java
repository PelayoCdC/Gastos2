package es.gastos.consultas.dominio;

import es.gastos.consultas.dominio.informes.AgregadorBizum;
import es.gastos.consultas.dominio.informes.Apunte;
import es.gastos.consultas.dominio.informes.DetectorRecurrentes;
import es.gastos.consultas.dominio.informes.Informes;
import es.gastos.consultas.dominio.informes.Seleccion;
import es.gastos.kernel.dominio.ClaveConcepto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Calculadores de informes y análisis; los casos de recurrentes y Bizum son los de {@code dominio.spec.ts} del móvil. */
class InformesTest {

	private static final Apunte.Referencia NOMINA = new Apunte.Referencia(UUID.randomUUID(), "Nomina");

	private static final Apunte.Referencia AHORRO = new Apunte.Referencia(UUID.randomUUID(), "Ahorro");

	private static final Apunte.Categoria OCIO = new Apunte.Categoria(UUID.randomUUID(), "Ocio", "#ff0000");

	private static final Apunte.Categoria ALIMENTACION = new Apunte.Categoria(UUID.randomUUID(), "Alimentacion", "#00ff00");

	private static final Apunte.Etiqueta BIZUM = new Apunte.Etiqueta(UUID.randomUUID(), "Bizum", null);

	private static final Apunte.Etiqueta CINE = new Apunte.Etiqueta(UUID.randomUUID(), "Cine", OCIO.id());

	private static final Apunte.Etiqueta SUPER = new Apunte.Etiqueta(UUID.randomUUID(), "Supermercado", ALIMENTACION.id());

	@Nested
	class Agregaciones {

		@Test
		void elResumenMensualSeparaGastosEIngresosYOrdenaLosMesesComoNumeros() {
			List<Apunte> apuntes = List.of(
					apunte("2026-10-02", "NOMINA", "1500.00", null, List.of()),
					apunte("2026-09-05", "LUPA", "-40.10", ALIMENTACION, List.of(SUPER)),
					apunte("2026-10-20", "CINE", "-12.50", OCIO, List.of(CINE)),
					apunte("2026-10-21", "LUPA", "-7.40", ALIMENTACION, List.of(SUPER)));

			List<Informes.ResumenMes> resumen = Informes.resumenMensual(apuntes, Seleccion.TODO);

			assertThat(resumen).extracting(Informes.ResumenMes::mes).containsExactly(9, 10);
			assertThat(resumen.get(1)).isEqualTo(new Informes.ResumenMes(2026, 10, new BigDecimal("19.90"), new BigDecimal("1500.00"),
					new BigDecimal("1480.10")));
			// Con categorías y etiquetas a la vez mandan las categorías
			assertThat(Informes.resumenMensual(apuntes, new Seleccion(Set.of(OCIO.id()), Set.of(SUPER.id()))))
					.singleElement().extracting(Informes.ResumenMes::gastos).isEqualTo(new BigDecimal("12.50"));
			assertThat(Informes.resumenMensual(apuntes, new Seleccion(null, Set.of(SUPER.id()))))
					.extracting(Informes.ResumenMes::gastos).containsExactly(new BigDecimal("40.10"), new BigDecimal("7.40"));
		}

		@Test
		void elSaldoDeCadaDiaEsElDelUltimoMovimientoConSaldoYLasCuentasSeDistinguenPorId() {
			Apunte.Referencia otraNomina = new Apunte.Referencia(UUID.randomUUID(), "Nomina");
			List<Apunte> apuntes = List.of(
					conSaldo(apunte("2026-07-01", "B", "-1", null, List.of()), "99.00", 2, NOMINA),
					conSaldo(apunte("2026-07-01", "A", "-1", null, List.of()), "100.00", 1, NOMINA),
					conSaldo(apunte("2026-07-02", "C", "-1", null, List.of()), null, 3, NOMINA),
					conSaldo(apunte("2026-07-01", "D", "-5", null, List.of()), "500.00", 1, otraNomina),
					conSaldo(apunte("2026-07-03", "E", "-5", null, List.of()), "10.00", 1, AHORRO));

			List<Informes.SerieSaldo> series = Informes.evolucionSaldo(apuntes);

			assertThat(series).hasSize(3);
			assertThat(series.getFirst().puntos()).as("el día 2 no tiene saldo conocido")
					.containsExactly(new Informes.PuntoSaldo(LocalDate.of(2026, 7, 1), new BigDecimal("99.00")));
			assertThat(series.get(1).cuenta()).isEqualTo(otraNomina);
		}

		@Test
		void losGastosPorCategoriaIncluyenUnGrupoSinCategoriaYVanDeMayorAMenor() {
			List<Informes.TotalCategoria> totales = Informes.gastosPorCategoria(List.of(
					apunte("2026-07-01", "LUPA", "-10", ALIMENTACION, List.of()),
					apunte("2026-07-02", "SIN NADA", "-30", null, List.of()),
					apunte("2026-07-03", "LUPA", "-25", ALIMENTACION, List.of()),
					apunte("2026-07-04", "NOMINA", "900", ALIMENTACION, List.of())));

			assertThat(totales).containsExactly(
					new Informes.TotalCategoria(ALIMENTACION, new BigDecimal("35.00"), 2),
					new Informes.TotalCategoria(null, new BigDecimal("30.00"), 1));
		}

		@Test
		void porEtiquetaUnMovimientoSumaEnCadaUnaYSeFiltraPorLaCategoriaDeLaEtiqueta() {
			List<Apunte> apuntes = List.of(apunte("2026-07-01", "CINE Y SUPER", "-20", OCIO, List.of(CINE, SUPER)),
					apunte("2026-07-02", "SUPER", "-5", ALIMENTACION, List.of(SUPER)));

			assertThat(Informes.gastosPorEtiqueta(apuntes, Set.of())).extracting(t -> t.etiqueta().nombre(), Informes.TotalEtiqueta::total)
					.containsExactly(org.assertj.core.groups.Tuple.tuple("Supermercado", new BigDecimal("25.00")),
							org.assertj.core.groups.Tuple.tuple("Cine", new BigDecimal("20.00")));
			assertThat(Informes.gastosPorEtiqueta(apuntes, Set.of(OCIO.id()))).singleElement()
					.extracting(t -> t.etiqueta().nombre()).isEqualTo("Cine");
		}

		@Test
		void elGastoDiarioYLaComposicionMensualSoloCuentanGastos() {
			List<Apunte> apuntes = List.of(apunte("2026-09-30", "A", "-1.10", OCIO, List.of()),
					apunte("2026-10-01", "B", "-2", null, List.of()), apunte("2026-10-01", "C", "-3", OCIO, List.of()),
					apunte("2026-10-01", "D", "50", OCIO, List.of()));

			assertThat(Informes.gastoDiario(apuntes, Seleccion.TODO)).containsExactly(
					new Informes.GastoDia(LocalDate.of(2026, 9, 30), new BigDecimal("1.10")),
					new Informes.GastoDia(LocalDate.of(2026, 10, 1), new BigDecimal("5.00")));
			assertThat(Informes.gastosMensualesPorCategoria(apuntes)).containsExactly(
					new Informes.GastoMesCategoria(2026, 9, OCIO, new BigDecimal("1.10")),
					new Informes.GastoMesCategoria(2026, 10, OCIO, new BigDecimal("3.00")),
					new Informes.GastoMesCategoria(2026, 10, null, new BigDecimal("2.00")));
		}

		@Test
		void elTopDeConceptosAgrupaPorLaClaveSinFechas() {
			List<Informes.TopConcepto> top = Informes.topConceptos(List.of(
					apunte("2026-02-04", "GOGALIA 04 02 2024", "-10", null, List.of()),
					apunte("2026-10-15", "gogalia 15 10 2023", "-20", null, List.of()),
					apunte("2026-10-16", "LUPA", "-5", null, List.of()),
					apunte("2026-10-17", "04-02-2024", "-99", null, List.of())), Seleccion.TODO, 15);

			assertThat(top).containsExactly(new Informes.TopConcepto("gogalia", new BigDecimal("30.00"), 2, new BigDecimal("15.00")),
					new Informes.TopConcepto("lupa", new BigDecimal("5.00"), 1, new BigDecimal("5.00")));
		}
	}

	@Nested
	class Recurrentes {

		private final LocalDate hoy = LocalDate.of(2026, 7, 23);

		@Test
		void detectaUnGastoMensualEstableYLoAnualiza() {
			List<Apunte> netflix = List.of(apunte("2026-04-15", "NETFLIX.COM", "-12.99", null, List.of()),
					apunte("2026-05-15", "NETFLIX.COM", "-12.99", null, List.of()),
					apunte("2026-06-15", "NETFLIX.COM", "-12.99", null, List.of()),
					apunte("2026-07-15", "NETFLIX.COM", "-12.99", null, List.of()));

			List<DetectorRecurrentes.Recurrente> recurrentes = DetectorRecurrentes.detectar(netflix, Seleccion.TODO, this.hoy);

			assertThat(recurrentes).singleElement().satisfies(r -> {
				assertThat(r.periodicidad()).isEqualTo(DetectorRecurrentes.Periodicidad.MENSUAL);
				assertThat(r.activo()).isTrue();
				assertThat(r.cadaDias()).isEqualTo(30);
				assertThat(r.costeAnual()).isEqualByComparingTo("158.05");
				assertThat(r.veces()).isEqualTo(4);
			});
			assertThat(DetectorRecurrentes.detectar(netflix, Seleccion.TODO, LocalDate.of(2026, 10, 1)).getFirst().activo())
					.as("más de dos periodos sin aparecer").isFalse();
		}

		@Test
		void descartaGruposConImporteInestableOSinPeriodicidadConocida() {
			assertThat(DetectorRecurrentes.detectar(List.of(apunte("2026-04-15", "GASOLINERA X", "-10", null, List.of()),
					apunte("2026-05-15", "GASOLINERA X", "-60", null, List.of()),
					apunte("2026-06-15", "GASOLINERA X", "-110", null, List.of())), Seleccion.TODO, this.hoy)).isEmpty();
			assertThat(DetectorRecurrentes.detectar(List.of(apunte("2026-04-15", "CADA 60 DIAS", "-10", null, List.of()),
					apunte("2026-06-14", "CADA 60 DIAS", "-10", null, List.of()),
					apunte("2026-08-13", "CADA 60 DIAS", "-10", null, List.of())), Seleccion.TODO, this.hoy)).isEmpty();
		}
	}

	@Nested
	class Bizum {

		private final List<Apunte> movimientos = List.of(
				// Con la palabra "bizum" y nota tras los dos puntos
				apunte("2026-06-10", "BIZUM: Cena familia", "-20", null, List.of(BIZUM)),
				// Recibidos sin la palabra: solo el nombre del emisor, pero etiquetados Bizum
				apunte("2026-06-15", "JESUS RUIZ BOLADO", "50", null, List.of(BIZUM)),
				apunte("2026-07-02", "JESUS RUIZ BOLADO", "6", null, List.of(BIZUM)),
				// Transferencia normal: no es Bizum
				apunte("2026-06-20", "MARIA PENA NORIEGA", "-30", null, List.of()),
				apunte("2026-06-21", "Bizum:", "-1", null, List.of()));

		@Test
		void detectaBizumsPorLaPalabraYPorLaEtiquetaYAgrupaPorContacto() {
			List<AgregadorBizum.ContactoBizum> contactos = AgregadorBizum.agrupar(this.movimientos, Seleccion.TODO);

			assertThat(contactos).extracting(AgregadorBizum.ContactoBizum::contacto)
					.containsExactly("jesus ruiz bolado", "cena familia", "(sin detalle)");
			assertThat(contactos.getFirst()).isEqualTo(new AgregadorBizum.ContactoBizum("jesus ruiz bolado", new BigDecimal("0.00"),
					new BigDecimal("56.00"), new BigDecimal("56.00"), 2));
			assertThat(contactos.get(1).neto()).isEqualByComparingTo("-20");
		}

		@Test
		void respetaElPeriodoYLaSeleccion() {
			List<Apunte> soloJulio = this.movimientos.stream().filter(a -> a.fecha().getMonthValue() == 7).toList();

			assertThat(AgregadorBizum.agrupar(soloJulio, Seleccion.TODO)).singleElement()
					.satisfies(c -> assertThat(c.veces()).isEqualTo(1));
			assertThat(AgregadorBizum.agrupar(this.movimientos, new Seleccion(Set.of(OCIO.id()), null))).isEmpty();
		}
	}

	private static Apunte apunte(String fecha, String concepto, String importe, Apunte.Categoria categoria, List<Apunte.Etiqueta> etiquetas) {
		return new Apunte(UUID.randomUUID(), LocalDate.parse(fecha), concepto, ClaveConcepto.de(concepto).valor(), new BigDecimal(importe),
				null, 0, null, categoria, etiquetas);
	}

	private static Apunte conSaldo(Apunte apunte, String saldo, int orden, Apunte.Referencia cuenta) {
		return new Apunte(apunte.id(), apunte.fecha(), apunte.concepto(), apunte.claveConcepto(), apunte.importe(),
				saldo == null ? null : new BigDecimal(saldo), orden, cuenta, apunte.categoria(), apunte.etiquetas());
	}
}
