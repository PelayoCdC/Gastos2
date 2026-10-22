package es.gastos.contratos;

import es.gastos.contratos.respaldo.v1.RespaldoV1;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del respaldo v1 (docs/adr/0020): la muestra tiene la forma exacta que escribe el back
 * antiguo y que lee la app móvil, y el record la lee y la vuelve a escribir sin perder ni renombrar
 * campos. Si este test falla, el móvil o los respaldos ya guardados dejarían de entenderse.
 */
class RespaldoV1Test {

	private static final JsonMapper JSON = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

	@Test
	void laMuestraDelBackAntiguoSeLeeYSeEscribeIgual() throws Exception {
		try (InputStream muestra = RespaldoV1Test.class.getResourceAsStream("/contratos/RespaldoV1.json")) {
			JsonNode aprobado = JSON.readTree(muestra);
			RespaldoV1 respaldo = JSON.treeToValue(aprobado, RespaldoV1.class);

			assertThat(respaldo.transacciones().getFirst().idsEtiquetas()).hasSize(1);
			JsonNode reescrito = JSON.valueToTree(respaldo);
			assertThat(reescrito).isEqualTo(aprobado);
		}
	}
}
