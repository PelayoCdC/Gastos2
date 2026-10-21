package es.gastos.kernel.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorTest {

	@Test
	void quitaTildesYPasaAMinusculasComoElBackAntiguo() {
		assertThat(Normalizador.normalizar("CAFÉ CON AZÚCAR")).isEqualTo("cafe con azucar");
		assertThat(Normalizador.normalizar("Ñoño")).isEqualTo("nono");
		assertThat(Normalizador.normalizar(null)).isEmpty();
	}

	@Test
	void normalizarConservaLosEspaciosDeLosExtremos() {
		// Las ocurrencias por defecto dependen de ellos: "bar " no debe casar con "barcelona"
		assertThat(Normalizador.normalizar("Bar ")).isEqualTo("bar ");
		assertThat(Normalizador.normalizar(" IBI")).isEqualTo(" ibi");
	}

	@Test
	void normalizarNombreColapsaYRecortaEspaciosIncluidoElEspacioDuro() {
		assertThat(Normalizador.normalizarNombre("  Ocio   y Cultura ")).isEqualTo("ocio y cultura");
		assertThat(Normalizador.normalizarNombre("Alimentación")).isEqualTo(Normalizador.normalizarNombre("ALIMENTACION"));
		assertThat(Normalizador.normalizarNombre("Banco y comisiones")).isEqualTo("banco y comisiones");
	}

	@Test
	void esBlancoTrataNullYEspaciosComoVacio() {
		assertThat(Normalizador.esBlanco(null)).isTrue();
		assertThat(Normalizador.esBlanco("   ")).isTrue();
		assertThat(Normalizador.esBlanco(" a ")).isFalse();
	}
}
