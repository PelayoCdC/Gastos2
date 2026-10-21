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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de movimientos de punta a punta contra MariaDB y Kafka reales. El JWT se simula con
 * {@code jwt()} de spring-security-test: aquí se prueba el servicio, no la emisión de tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class MovimientosApiIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private final String ana = UUID.randomUUID().toString();

	private final String luis = UUID.randomUUID().toString();

	@Test
	void cuentasConNombreUnicoSaldoYConteo() throws Exception {
		String cuenta = this.crearCuenta(this.ana, "Nómina");

		this.mvc.perform(como(this.ana, post("/cuentas")).content("{\"nombre\":\"  NOMINA \"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NOMBRE_DUPLICADO"));
		// El mismo nombre en otro usuario no choca
		this.crearCuenta(this.luis, "Nómina");

		this.registrarTransaccion(this.ana, cuenta, "-12.30");
		this.mvc.perform(como(this.ana, get("/cuentas")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(cuenta))
				.andExpect(jsonPath("$[0].numeroTransacciones").value(1))
				// Las manuales no traen saldo: el saldo de la cuenta sigue sin conocerse
				.andExpect(jsonPath("$[0].saldo").doesNotExist());

		this.mvc.perform(como(this.ana, put("/cuentas/" + cuenta)).content("{\"nombre\":\"Cuenta nómina\",\"banco\":\"Unicaja\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Cuenta nómina"))
				.andExpect(jsonPath("$.banco").value("Unicaja"));
	}

	@Test
	void lasTransaccionesSeCreanCorrigenYBorranYSusEventosVanAlOutbox() throws Exception {
		String cuenta = this.crearCuenta(this.ana, "Diario");
		String transaccion = this.registrarTransaccion(this.ana, cuenta, "-45.6");

		this.mvc.perform(como(this.ana, get("/transacciones/" + transaccion)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.importe").value(-45.60))
				.andExpect(jsonPath("$.origen").value("MANUAL"));

		this.mvc.perform(como(this.ana, put("/transacciones/" + transaccion)).content("""
						{"cuentaId":"%s","fechaContable":"2026-10-20","concepto":"COMPRA LUPA","nota":"semanal","importe":-50}
						""".formatted(cuenta)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.concepto").value("COMPRA LUPA"))
				.andExpect(jsonPath("$.nota").value("semanal"));

		this.mvc.perform(como(this.ana, delete("/transacciones/" + transaccion))).andExpect(status().isNoContent());
		this.mvc.perform(como(this.ana, get("/transacciones/" + transaccion))).andExpect(status().isNotFound());

		List<String> tipos = this.jdbc.sql("SELECT tipo FROM outbox WHERE agregado_id = :id ORDER BY id")
				.param("id", UUID.fromString(transaccion)).query(String.class).list();
		assertThat(tipos).containsExactly("TransaccionRegistrada", "TransaccionCorregida", "TransaccionEliminada");
		List<Long> versiones = this.jdbc.sql("SELECT version_agregado FROM outbox WHERE agregado_id = :id ORDER BY id")
				.param("id", UUID.fromString(transaccion)).query(Long.class).list();
		assertThat(versiones).containsExactly(1L, 2L, 3L);
	}

	@Test
	void losRecursosAjenosNoExisten() throws Exception {
		String cuenta = this.crearCuenta(this.ana, "Privada");
		String transaccion = this.registrarTransaccion(this.ana, cuenta, "-1");

		this.mvc.perform(como(this.luis, get("/cuentas/" + cuenta))).andExpect(status().isNotFound());
		this.mvc.perform(como(this.luis, get("/transacciones/" + transaccion))).andExpect(status().isNotFound());
		this.mvc.perform(como(this.luis, delete("/transacciones/" + transaccion))).andExpect(status().isNotFound());
		// Tampoco se puede apuntar una transacción propia a una cuenta ajena
		this.mvc.perform(como(this.luis, post("/transacciones")).content("""
						{"cuentaId":"%s","fechaContable":"2026-10-21","concepto":"x","importe":-1}
						""".formatted(cuenta)))
				.andExpect(status().isNotFound());
		this.mvc.perform(get("/cuentas")).andExpect(status().isUnauthorized());
	}

	@Test
	void unaCuentaConMovimientosSoloSeBorraSiSePideExplicitamente() throws Exception {
		String cuenta = this.crearCuenta(this.ana, "Para borrar");
		String transaccion = this.registrarTransaccion(this.ana, cuenta, "-5");

		this.mvc.perform(como(this.ana, delete("/cuentas/" + cuenta)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CUENTA_CON_MOVIMIENTOS"));

		this.mvc.perform(como(this.ana, delete("/cuentas/" + cuenta).param("conMovimientos", "true")))
				.andExpect(status().isNoContent());
		this.mvc.perform(como(this.ana, get("/cuentas/" + cuenta))).andExpect(status().isNotFound());
		this.mvc.perform(como(this.ana, get("/transacciones/" + transaccion))).andExpect(status().isNotFound());
	}

	@Test
	void lasEntradasMalformadasSon400ConCodigo() throws Exception {
		this.mvc.perform(como(this.ana, post("/transacciones")).content("{\"fechaContable\":\"2026-10-21\",\"concepto\":\"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores[0].campo").value("importe"));
		this.mvc.perform(como(this.ana, get("/transacciones/no-es-un-uuid")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
		this.mvc.perform(como(this.ana, post("/transacciones")).content("{\"fechaContable\":\"31/02/2026\",\"importe\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PETICION_MALFORMADA"));
	}

	private String crearCuenta(String usuario, String nombre) throws Exception {
		String respuesta = this.mvc.perform(como(usuario, post("/cuentas")).content("{\"nombre\":\"" + nombre + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.id");
	}

	private String registrarTransaccion(String usuario, String cuenta, String importe) throws Exception {
		String respuesta = this.mvc.perform(como(usuario, post("/transacciones")).content("""
						{"cuentaId":"%s","fechaContable":"2026-10-21","concepto":"COMPRA","importe":%s}
						""".formatted(cuenta, importe)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.id");
	}

	private static MockHttpServletRequestBuilder como(String usuario, MockHttpServletRequestBuilder peticion) {
		RequestPostProcessor token = jwt().jwt(j -> j.subject(usuario));
		return peticion.with(token).contentType(MediaType.APPLICATION_JSON);
	}
}
