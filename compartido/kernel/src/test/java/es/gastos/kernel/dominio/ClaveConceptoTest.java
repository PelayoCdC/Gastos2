package es.gastos.kernel.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Casos portados de {@code gastos-movil/src/app/core/dominio/dominio.spec.ts}. */
class ClaveConceptoTest {

	@Test
	void quitaLasFechasQueIncrustanLosBancos() {
		assertThat(ClaveConcepto.de("LA BUHARDILLA 08 08 2024").valor()).isEqualTo("la buhardilla");
		assertThat(ClaveConcepto.de("T/BAR 25-12-2019").valor()).isEqualTo("t/bar");
		assertThat(ClaveConcepto.de("GOGALIA 04 02 2024")).isEqualTo(ClaveConcepto.de("GOGALIA 15 10 2023"));
	}

	@Test
	void exigeElMismoSeparadorEnTodaLaFecha() {
		// Separadores mezclados no son una fecha: no se quita
		assertThat(ClaveConcepto.de("PAGO 12-05 2024").valor()).isEqualTo("pago 12-05 2024");
	}

	@Test
	void admiteFormatoAnioMesDiaYColapsaEspacios() {
		assertThat(ClaveConcepto.de("COMPRA  2024-05-12   WEB").valor()).isEqualTo("compra web");
	}

	@Test
	void quitaTildesYTrataNullComoClaveVacia() {
		assertThat(ClaveConcepto.de("CAFETERÍA Ñ 01/02/24").valor()).isEqualTo("cafeteria n");
		assertThat(ClaveConcepto.de(null).esVacia()).isTrue();
		assertThat(ClaveConcepto.de("   ").esVacia()).isTrue();
	}
}
