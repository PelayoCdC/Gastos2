package es.gastos.compartido.pruebas.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import es.gastos.kernel.aplicacion.CasoDeUso;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Reglas de arquitectura comunes a todos los servicios (Clean + Hexagonal).
 *
 * <p>Cada servicio tiene un test {@code ArquitecturaTest} que llama a {@link #comprobar(String)}
 * con su paquete base. Si alguien rompe la regla de dependencia, el build falla: la arquitectura
 * deja de ser una convención y pasa a ser verificable. Ver {@code docs/arquitectura/03-componentes-c4.md}.
 */
public final class ReglasArquitectura {

	private static final String DOMINIO = "..dominio..";
	private static final String APLICACION = "..aplicacion..";
	private static final String INFRAESTRUCTURA = "..infraestructura..";

	private ReglasArquitectura() {
	}

	/** Comprueba todas las reglas sobre las clases de producción del paquete indicado. */
	public static void comprobar(String paqueteBase) {
		JavaClasses clases = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages(paqueteBase);
		comprobar(clases, paqueteBase);
	}

	public static void comprobar(JavaClasses clases, String paqueteBase) {
		capas().check(clases);
		nucleoSinFrameworks().check(clases);
		nucleoSinRelojImplicito().check(clases);
		casosDeUsoEnAplicacion().check(clases);
		sinCiclosEntreModulosDeDominio(paqueteBase).check(clases);
	}

	/**
	 * La regla de dependencia: infraestructura → aplicación → dominio, nunca al revés.
	 * Los adaptadores (infraestructura) conocen los puertos; los puertos no conocen adaptadores.
	 */
	public static ArchRule capas() {
		return layeredArchitecture()
				.consideringOnlyDependenciesInLayers()
				.layer("Dominio").definedBy(DOMINIO)
				.layer("Aplicacion").definedBy(APLICACION)
				.layer("Infraestructura").definedBy(INFRAESTRUCTURA)
				.whereLayer("Infraestructura").mayNotBeAccessedByAnyLayer()
				.whereLayer("Aplicacion").mayOnlyBeAccessedByLayers("Infraestructura")
				.whereLayer("Dominio").mayOnlyBeAccessedByLayers("Aplicacion", "Infraestructura")
				.because("las dependencias solo pueden apuntar hacia dentro (docs/adr/0006)");
	}

	/** Dominio y aplicación son Java puro: los frameworks solo entran por los adaptadores. */
	public static ArchRule nucleoSinFrameworks() {
		return noClasses().that().resideInAnyPackage(DOMINIO, APLICACION)
				.should().dependOnClassesThat().resideInAnyPackage(
						"org.springframework..",
						"jakarta.persistence..",
						"jakarta.servlet..",
						"org.hibernate..",
						"tools.jackson..",
						"com.fasterxml.jackson..",
						"org.apache.kafka..",
						"org.apache.poi..",
						"lombok..")
				.because("dominio y aplicación no dependen de frameworks (docs/adr/0006)");
	}

	/** La hora se obtiene de un {@code Clock} inyectado para que el comportamiento sea reproducible en tests. */
	public static ArchRule nucleoSinRelojImplicito() {
		return noClasses().that().resideInAnyPackage(DOMINIO, APLICACION)
				.should().callMethod(LocalDate.class, "now")
				.orShould().callMethod(LocalDateTime.class, "now")
				.orShould().callMethod(ZonedDateTime.class, "now")
				.orShould().callMethod(Instant.class, "now")
				.orShould().callMethod(System.class, "currentTimeMillis")
				.because("la fecha actual se inyecta con java.time.Clock");
	}

	public static ArchRule casosDeUsoEnAplicacion() {
		return classes().that().areAnnotatedWith(CasoDeUso.class)
				.should().resideInAPackage(APLICACION)
				.allowEmptyShould(true);
	}

	/** Los submódulos del dominio de un servicio (agregados) no forman ciclos entre sí. */
	public static ArchRule sinCiclosEntreModulosDeDominio(String paqueteBase) {
		return slices().matching(paqueteBase + ".dominio.(*)..")
				.should().beFreeOfCycles()
				.allowEmptyShould(true);
	}
}
