package es.gastos.contratos;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de contrato: cada evento del catálogo tiene una muestra JSON aprobada
 * ({@code src/test/resources/contratos/<Tipo>-v<version>.json}) y el record la lee y la vuelve a
 * escribir sin perder ni renombrar campos. Si alguien renombra un campo de un evento publicado,
 * este test falla: cambiar el contrato exige una versión nueva.
 */
class ContratosTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	static List<CatalogoEventos.Entrada> eventos() {
		return CatalogoEventos.todos();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("eventos")
	void laMuestraAprobadaSeLeeYSeEscribeIgual(CatalogoEventos.Entrada entrada) throws Exception {
		String recurso = "/contratos/" + entrada.tipo() + "-v" + entrada.version() + ".json";

		try (InputStream muestra = ContratosTest.class.getResourceAsStream(recurso)) {
			assertThat(muestra).as("Falta la muestra aprobada " + recurso).isNotNull();

			JsonNode aprobado = JSON.readTree(muestra);
			Object evento = JSON.treeToValue(aprobado, entrada.clase());
			JsonNode reescrito = JSON.valueToTree(evento);

			assertThat(reescrito).isEqualTo(aprobado);
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("eventos")
	void cadaEventoVaAlTopicDeSuContexto(CatalogoEventos.Entrada entrada) {
		String contexto = entrada.clase().getPackageName().split("\\.")[3];

		assertThat(entrada.topic()).isEqualTo(contexto + ".eventos");
		assertThat(entrada.clase().getPackageName()).endsWith(".v" + entrada.version());
	}
}
