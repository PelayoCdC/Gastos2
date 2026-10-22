package es.gastos.movimientos;

import com.jayway.jsonpath.JsonPath;
import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parte de movimientos de la restauración de un respaldo (F10, docs/adr/0020). */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class RespaldoIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private final String usuario = UUID.randomUUID().toString();

	private final String cuenta = UUID.randomUUID().toString();

	private final String documento = UUID.randomUUID().toString();

	private final String transaccion = UUID.randomUUID().toString();

	@Test
	void simularNoTocaNadaYSustituirReemplazaTodoConservandoLosIdsYSePuedeRepetir() throws Exception {
		String anterior = JsonPath.read(this.mvc.perform(como(post("/cuentas")).content("{\"nombre\":\"Anterior\"}"))
				.andReturn().getResponse().getContentAsString(), "$.id");

		this.mvc.perform(como(put("/internal/respaldos/v1").param("simular", "true")).content(this.respaldo()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cuentas").value(1))
				.andExpect(jsonPath("$.transacciones").value(1))
				.andExpect(jsonPath("$.anomalias.length()").value(1));
		this.mvc.perform(como(get("/cuentas/" + anterior))).andExpect(status().isOk());

		for (int vez = 0; vez < 2; vez++) {
			this.mvc.perform(como(put("/internal/respaldos/v1")).content(this.respaldo()))
					.andExpect(status().isOk())
					.andExpect(header().string("Gastos-Secuencia", org.hamcrest.Matchers.startsWith("movimientos=")));
		}

		this.mvc.perform(como(get("/cuentas/" + anterior))).andExpect(status().isNotFound());
		this.mvc.perform(como(get("/cuentas/" + this.cuenta)))
				.andExpect(jsonPath("$.nombre").value("Diario"))
				.andExpect(jsonPath("$.saldo").value(898.5))
				.andExpect(jsonPath("$.numeroTransacciones").value(1));
		this.mvc.perform(como(get("/transacciones/" + this.transaccion)))
				.andExpect(jsonPath("$.origen").value("MIGRACION"))
				.andExpect(jsonPath("$.documentoId").value(this.documento));

		// Lo primero de cada restauración es el evento de conjunto; después, el alta de cada cosa
		List<String> tipos = this.jdbc.sql("SELECT tipo FROM outbox WHERE usuario_id = ? ORDER BY id")
				.param(UUID.fromString(this.usuario)).query(String.class).list();
		assertThat(tipos).endsWith("MovimientosRestaurados", "CuentaGuardada", "DocumentoRegistrado", "TransaccionRegistrada");
		assertThat(tipos.stream().filter("MovimientosRestaurados"::equals)).hasSize(2);
	}

	@Test
	void elRespaldoDeOtraCuentaSeRechazaSinTocarNadaPorqueSusIdsYaEstanEnUso() throws Exception {
		this.mvc.perform(como(put("/internal/respaldos/v1")).content(this.respaldo())).andExpect(status().isOk());

		String otro = UUID.randomUUID().toString();
		String suyaAntes = JsonPath.read(this.mvc.perform(post("/cuentas").with(jwt().jwt(j -> j.subject(otro)))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Suya\"}")).andReturn().getResponse().getContentAsString(), "$.id");
		for (String simular : List.of("true", "false")) {
			this.mvc.perform(put("/internal/respaldos/v1").param("simular", simular).with(jwt().jwt(j -> j.subject(otro)))
							.contentType(MediaType.APPLICATION_JSON).content(this.respaldo()))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("RESPALDO_DE_OTRA_CUENTA"));
		}
		this.mvc.perform(get("/cuentas/" + suyaAntes).with(jwt().jwt(j -> j.subject(otro)))).andExpect(status().isOk());
		this.mvc.perform(como(get("/cuentas/" + this.cuenta))).andExpect(jsonPath("$.nombre").value("Diario"));
	}

	private String respaldo() {
		return """
				{"cuentas":[{"id":"%s","nombre":"Diario","banco":"Unicaja","descripcion":null}],
				 "documentos":[{"id":"%s","nombre":"Julio","nombreArchivo":"julio.xlsx","fecha":"2026-07-31","cuentaId":"%s"}],
				 "transacciones":[
				   {"id":"%s","concepto":"COMPRA LUPA","nota":null,"importe":-12.34,"saldo":898.50,"orden":3,"ordenDocumento":1,
				    "fechaContable":"2026-07-01","fechaValor":null,"cuentaId":"%s","documentoId":"%s"},
				   {"id":"%s","concepto":"SIN FECHA","nota":null,"importe":-1,"saldo":0,"orden":4,"ordenDocumento":2,
				    "fechaContable":null,"fechaValor":null,"cuentaId":"%s","documentoId":"%s"}]}"""
				.formatted(this.cuenta, this.documento, this.cuenta, this.transaccion, this.cuenta, this.documento, UUID.randomUUID(),
						this.cuenta, this.documento);
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion) {
		return peticion.with(jwt().jwt(j -> j.subject(this.usuario))).contentType(MediaType.APPLICATION_JSON);
	}
}
