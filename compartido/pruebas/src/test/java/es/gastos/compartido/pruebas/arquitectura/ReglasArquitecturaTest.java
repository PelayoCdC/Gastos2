package es.gastos.compartido.pruebas.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Comprueba que las reglas aceptan un servicio bien construido y detectan las violaciones. */
class ReglasArquitecturaTest {

	private static final JavaClasses CORRECTO = new ClassFileImporter().importPackages("es.gastos.ejemplo.correcto");

	private static final JavaClasses INCORRECTO = new ClassFileImporter().importPackages("es.gastos.ejemplo.incorrecto");

	@Test
	void unServicioBienConstruidoCumpleTodasLasReglas() {
		assertThatCode(() -> ReglasArquitectura.comprobar(CORRECTO, "es.gastos.ejemplo.correcto"))
				.doesNotThrowAnyException();
	}

	@Test
	void detectaUnDominioQueDependeDeInfraestructura() {
		assertThatThrownBy(() -> ReglasArquitectura.capas().check(INCORRECTO))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("CosaMala");
	}

	@Test
	void detectaFrameworksEnElNucleo() {
		assertThatThrownBy(() -> ReglasArquitectura.nucleoSinFrameworks().check(INCORRECTO))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("org.springframework.util.StringUtils");
	}

	@Test
	void detectaElUsoDelRelojDelSistema() {
		assertThatThrownBy(() -> ReglasArquitectura.nucleoSinRelojImplicito().check(INCORRECTO))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("UsaRelojImplicito");
	}
}
