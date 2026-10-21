package es.gastos.kernel.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImporteTest {

	@Test
	void normalizaSiempreADosDecimales() {
		assertThat(Importe.de("1.5")).isEqualTo(Importe.de("1.50"));
		assertThat(Importe.de("-12.345").comoTexto()).isEqualTo("-12.35");
		assertThat(Importe.de(new BigDecimal("1E+3")).comoTexto()).isEqualTo("1000.00");
	}

	@Test
	void conservaLosCentimosDondeElFloatAntiguoLosPerdia() {
		// Por encima de 2^18 el float tiene saltos de 0.03125: 262144.01 se guardaba como 262144.0
		Importe importe = Importe.de("262144.01");
		assertThat(importe.comoTexto()).isEqualTo("262144.01");
		assertThat(Importe.de(0.1).sumar(Importe.de(0.2))).isEqualTo(Importe.de("0.30"));
	}

	@Test
	void elSignoIndicaGastoOIngreso() {
		assertThat(Importe.de("-3").esNegativo()).isTrue();
		assertThat(Importe.de("3").esPositivo()).isTrue();
		assertThat(Importe.CERO.esCero()).isTrue();
		assertThat(Importe.de("-3").negar()).isEqualTo(Importe.de("3"));
		assertThat(Importe.de("-3").abs()).isEqualTo(Importe.de("3"));
	}

	@Test
	void rechazaTextoNoNumericoYValoresFueraDeRango() {
		assertThatThrownBy(() -> Importe.de("abc"))
				.isInstanceOf(ExcepcionDominio.class)
				.extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("IMPORTE_INVALIDO");
		assertThatThrownBy(() -> Importe.de("10000000000000.00"))
				.isInstanceOf(ExcepcionDominio.class)
				.extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("IMPORTE_FUERA_DE_RANGO");
	}

	@Test
	void seOrdenaPorValor() {
		assertThat(Importe.de("-10")).isLessThan(Importe.de("2"));
	}
}
