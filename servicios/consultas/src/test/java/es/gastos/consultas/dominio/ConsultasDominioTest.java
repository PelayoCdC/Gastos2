package es.gastos.consultas.dominio;

import es.gastos.consultas.dominio.consistencia.SecuenciasEsperadas;
import es.gastos.consultas.dominio.transacciones.FiltroTransacciones;
import es.gastos.consultas.dominio.transacciones.Ordenacion;
import es.gastos.kernel.dominio.ExcepcionDominio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultasDominioTest {

	@Test
	void elFiltroNormalizaElConceptoYRechazaRangosAlReves() {
		FiltroTransacciones filtro = new FiltroTransacciones("  Cafetería ", null, null, null, null, null, null, null, false, null, false);

		assertThat(filtro.concepto()).isEqualTo("Cafetería");
		assertThat(filtro.conceptoNormalizado()).isEqualTo("cafeteria");
		assertThat(filtro.filtraCategorias()).isFalse();
		assertThat(new FiltroTransacciones(" ", null, null, null, null, null, null, Set.of(), true, Set.of(), false).concepto()).isNull();

		assertThatThrownBy(() -> new FiltroTransacciones(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, null, null,
				null, false, null, false)).isInstanceOf(ExcepcionDominio.class).hasMessageContaining("desde");
		assertThatThrownBy(() -> new FiltroTransacciones(null, null, null, BigDecimal.TEN, BigDecimal.ONE, null, null, null, false, null,
				false)).isInstanceOf(ExcepcionDominio.class);
	}

	@Test
	void laOrdenacionSoloAdmiteCamposDeLaListaBlanca() {
		assertThat(Ordenacion.de(null, null)).isEqualTo(Ordenacion.POR_DEFECTO);
		assertThat(Ordenacion.de(null, "asc")).isEqualTo(new Ordenacion(Ordenacion.Campo.FECHA_CONTABLE, true));
		assertThat(Ordenacion.de("importe", null)).isEqualTo(new Ordenacion(Ordenacion.Campo.IMPORTE, true));
		assertThat(Ordenacion.de("categoria", "DESC")).isEqualTo(new Ordenacion(Ordenacion.Campo.CATEGORIA, false));

		assertThatThrownBy(() -> Ordenacion.de("usuario.password", null)).isInstanceOf(ExcepcionDominio.class)
				.extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("ORDEN_INVALIDO");
		assertThatThrownBy(() -> Ordenacion.de("importe", "arriba")).isInstanceOf(ExcepcionDominio.class);
	}

	@Test
	void lasSecuenciasEsperadasSeLeenDeLaCabeceraYSeComparanPorContexto() {
		SecuenciasEsperadas esperadas = SecuenciasEsperadas.de("movimientos=18, clasificacion=7,movimientos=12");

		assertThat(esperadas.porContexto()).containsExactlyInAnyOrderEntriesOf(Map.of("movimientos", 18L, "clasificacion", 7L));
		assertThat(esperadas.alcanzadasPor(Map.of("movimientos", 18L, "clasificacion", 9L))).isTrue();
		assertThat(esperadas.alcanzadasPor(Map.of("movimientos", 18L))).as("sin posición es 0").isFalse();
		assertThat(esperadas.soloDe("movimientos"::equals).alcanzadasPor(Map.of("movimientos", 20L))).isTrue();

		assertThat(SecuenciasEsperadas.de(null).vacia()).isTrue();
		assertThatThrownBy(() -> SecuenciasEsperadas.de("movimientos:18")).isInstanceOf(ExcepcionDominio.class)
				.extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("CABECERA_INVALIDA");
		assertThatThrownBy(() -> SecuenciasEsperadas.de("movimientos=-1")).isInstanceOf(ExcepcionDominio.class);
	}
}
