package es.gastos.movimientos;

import com.jayway.jsonpath.JsonPath;
import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.aplicacion.extractos.RegistrarExtracto;
import es.gastos.movimientos.aplicacion.extractos.ResultadoExtracto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registro de extractos (F4): duplicados por conteo, idempotencia, concurrencia y documentos. */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class ExtractosIT {

	private static final String TRES_LINEAS = """
			[{"fila":5,"fechaContable":"2026-10-02","fechaValor":"2026-10-01","concepto":"CAFE BAR PEPE","importe":-1.50,"saldo":998.50},
			 {"fila":6,"fechaContable":"2026-10-02","concepto":"CAFE BAR PEPE","importe":-1.50,"saldo":997.00},
			 {"fila":7,"fechaContable":"2026-10-03","concepto":"COMPRA LUPA 03 10 2026","importe":-45.60,"saldo":951.40}]""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private RegistrarExtracto registrarExtracto;

	private final String usuario = UUID.randomUUID().toString();

	@Test
	void importaLasFilasIdenticasYAlReimportarLasMarcaComoDuplicadas() throws Exception {
		String cuenta = this.crearCuenta("Diario");

		this.registrar(UUID.randomUUID(), cuenta, TRES_LINEAS)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.importadas").value(3))
				.andExpect(jsonPath("$.duplicados").isEmpty());

		this.registrar(UUID.randomUUID(), cuenta, TRES_LINEAS)
				.andExpect(jsonPath("$.importadas").value(0))
				.andExpect(jsonPath("$.duplicados.length()").value(3))
				.andExpect(jsonPath("$.duplicados[0].fila").value(5))
				.andExpect(jsonPath("$.duplicados[2].concepto").value("COMPRA LUPA 03 10 2026"));

		// El saldo de la cuenta es el del último movimiento con saldo (fecha y orden más altos)
		this.mvc.perform(como(get("/cuentas/" + cuenta)))
				.andExpect(jsonPath("$.saldo").value(951.40))
				.andExpect(jsonPath("$.numeroTransacciones").value(3));
	}

	@Test
	void unExtractoQueSeSolapaYUnaManualSinCuentaCuentanComoYaRegistrados() throws Exception {
		String cuenta = this.crearCuenta("Solapes");
		this.registrar(UUID.randomUUID(), cuenta, """
				[{"fila":1,"fechaContable":"2026-10-03","concepto":"COMPRA LUPA 03 10 2026","importe":-45.60}]""");
		// Una manual sin cuenta con los mismos hechos que una fila nueva también la convierte en duplicada
		this.mvc.perform(como(post("/transacciones")).content("""
				{"fechaContable":"2026-10-04","concepto":"farmacia  CENTRO","importe":-12}"""))
				.andExpect(status().isCreated());

		this.registrar(UUID.randomUUID(), cuenta, """
						[{"fila":1,"fechaContable":"2026-10-03","concepto":"COMPRA LUPA 03 10 2026","importe":-45.60},
						 {"fila":2,"fechaContable":"2026-10-04","concepto":"FARMACIA CENTRO","importe":-12.00},
						 {"fila":3,"fechaContable":"2026-10-05","concepto":"GASOLINERA","importe":-60}]""")
				.andExpect(jsonPath("$.importadas").value(1))
				.andExpect(jsonPath("$.duplicados[*].fila").value(org.hamcrest.Matchers.contains(1, 2)));
	}

	@Test
	void reintentarConElMismoExtractoIdDevuelveElMismoResultadoSinImportarOtraVez() throws Exception {
		String cuenta = this.crearCuenta("Reintentos");
		UUID extracto = UUID.randomUUID();

		String primera = this.registrar(extracto, cuenta, TRES_LINEAS).andReturn().getResponse().getContentAsString();
		String segunda = this.registrar(extracto, cuenta, TRES_LINEAS).andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<Integer>read(segunda, "$.importadas")).isEqualTo(3);
		assertThat(segunda).isEqualTo(primera);
		assertThat(this.contarTransacciones(cuenta)).isEqualTo(3);
	}

	@Test
	void dosImportacionesSimultaneasDelMismoExtractoNoDuplicanMovimientos() throws Exception {
		String cuenta = this.crearCuenta("Concurrencia");
		RegistrarExtracto.Comando comando = this.comando(cuenta);

		try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
			Callable<ResultadoExtracto> importar = () -> this.registrarExtracto.ejecutar(
					new RegistrarExtracto.Comando(comando.usuario(), UUID.randomUUID(), comando.cuentaId(), null, "a.xlsx", comando.lineas()));
			List<Future<ResultadoExtracto>> resultados = hilos.invokeAll(List.of(importar, importar));

			int importadas = 0;
			for (Future<ResultadoExtracto> resultado : resultados) {
				importadas += resultado.get().importadas();
			}
			assertThat(importadas).isEqualTo(3);
		}
		assertThat(this.contarTransacciones(cuenta)).isEqualTo(3);
	}

	@Test
	void reasignarYBorrarUnDocumentoArrastraASusTransaccionesConUnSoloEvento() throws Exception {
		String origen = this.crearCuenta("Origen");
		String destino = this.crearCuenta("Destino");
		UUID extracto = UUID.randomUUID();
		this.registrar(extracto, origen, TRES_LINEAS).andExpect(status().isCreated());
		String transaccion = this.jdbc.sql("SELECT id FROM transaccion WHERE documento_id = ? LIMIT 1")
				.param(extracto).query(String.class).single();

		this.mvc.perform(como(put("/documentos/" + extracto + "/cuenta")).content("{\"cuentaId\":\"" + destino + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cuenta.id").value(destino))
				.andExpect(jsonPath("$.numeroTransacciones").value(3));
		this.mvc.perform(como(get("/transacciones/" + transaccion))).andExpect(jsonPath("$.cuentaId").value(destino));

		this.mvc.perform(como(delete("/documentos/" + extracto))).andExpect(status().isNoContent());
		this.mvc.perform(como(get("/transacciones/" + transaccion))).andExpect(status().isNotFound());

		List<String> eventosDelDocumento = this.jdbc.sql("SELECT tipo FROM outbox WHERE agregado_id = ? ORDER BY id")
				.param(extracto).query(String.class).list();
		assertThat(eventosDelDocumento).containsExactly("DocumentoRegistrado", "DocumentoReasignado", "DocumentoEliminado");
		Long primeraTransaccion = this.jdbc.sql("""
						SELECT MIN(o.id) FROM outbox o WHERE o.tipo = 'TransaccionRegistrada'
						AND o.agregado_id IN (SELECT t.agregado_id FROM outbox t WHERE t.datos LIKE ?)""")
				.param("%" + extracto + "%").query(Long.class).single();
		Long registroDocumento = this.jdbc.sql("SELECT id FROM outbox WHERE agregado_id = ? AND tipo = 'DocumentoRegistrado'")
				.param(extracto).query(Long.class).single();
		assertThat(registroDocumento).as("el documento se anuncia antes que sus transacciones").isLessThan(primeraTransaccion);
	}

	@Test
	void laApiInternaDaLosDatosParaElReadThroughDeClasificacionSoloAlPropietario() throws Exception {
		String cuenta = this.crearCuenta("Interna");
		UUID extracto = UUID.randomUUID();
		this.registrar(extracto, cuenta, TRES_LINEAS);
		String transaccion = this.jdbc.sql("SELECT id FROM transaccion WHERE documento_id = ? AND orden = 7")
				.param(extracto).query(String.class).single();

		this.mvc.perform(como(get("/internal/transacciones/" + transaccion)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.documentoId").value(extracto.toString()))
				.andExpect(jsonPath("$.concepto").value("COMPRA LUPA 03 10 2026"))
				.andExpect(jsonPath("$.origen").value("IMPORTACION"));
		this.mvc.perform(get("/internal/transacciones/" + transaccion).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
				.andExpect(status().isNotFound());
		this.mvc.perform(como(post("/internal/transacciones/consulta"))
						.content("{\"ids\":[\"" + transaccion + "\",\"" + UUID.randomUUID() + "\"]}"))
				.andExpect(jsonPath("$.length()").value(1));
		// Un extracto contra una cuenta ajena no existe
		this.mvc.perform(post("/internal/extractos").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
						.contentType(MediaType.APPLICATION_JSON)
						.content(cuerpoExtracto(UUID.randomUUID(), cuenta, TRES_LINEAS)))
				.andExpect(status().isNotFound());
	}

	private org.springframework.test.web.servlet.ResultActions registrar(UUID extracto, String cuenta, String lineas) throws Exception {
		return this.mvc.perform(como(post("/internal/extractos")).content(cuerpoExtracto(extracto, cuenta, lineas)));
	}

	private static String cuerpoExtracto(UUID extracto, String cuenta, String lineas) {
		return """
				{"extractoId":"%s","cuentaId":"%s","nombreArchivo":"movimientos.xlsx","lineas":%s}
				""".formatted(extracto, cuenta, lineas);
	}

	private RegistrarExtracto.Comando comando(String cuenta) {
		LocalDate dia = LocalDate.of(2026, 10, 2);
		return new RegistrarExtracto.Comando(new UsuarioId(UUID.fromString(this.usuario)), UUID.randomUUID(), UUID.fromString(cuenta),
				null, "a.xlsx", List.of(
				new RegistrarExtracto.Linea(1, dia, null, "CAFE", new BigDecimal("-1.50"), null),
				new RegistrarExtracto.Linea(2, dia, null, "CAFE", new BigDecimal("-1.50"), null),
				new RegistrarExtracto.Linea(3, dia, null, "LUPA", new BigDecimal("-45.60"), null)));
	}

	private String crearCuenta(String nombre) throws Exception {
		String respuesta = this.mvc.perform(como(post("/cuentas")).content("{\"nombre\":\"" + nombre + "\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.id");
	}

	private long contarTransacciones(String cuenta) {
		return this.jdbc.sql("SELECT COUNT(*) FROM transaccion WHERE cuenta_id = ?").param(UUID.fromString(cuenta)).query(Long.class).single();
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion) {
		return peticion.with(jwt().jwt(j -> j.subject(this.usuario))).contentType(MediaType.APPLICATION_JSON);
	}
}
