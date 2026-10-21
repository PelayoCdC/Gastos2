package es.gastos.clasificacion;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import es.gastos.contratos.Topics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clasificación de transacciones (F6): automática al importar (eventos de movimientos), manual con
 * read-through a movimientos (simulado con un HttpServer), propagación y aplicación en bloque.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class ClasificacionIT {

	/** Transacciones que "existen" en el movimientos simulado: id → concepto. */
	private static final Map<String, String> EN_MOVIMIENTOS = new ConcurrentHashMap<>();

	private static final List<String> AUTORIZACIONES_RECIBIDAS = new CopyOnWriteArrayList<>();

	private static final HttpServer MOVIMIENTOS = movimientosSimulado();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private KafkaTemplate<String, String> kafka;

	private final String usuario = UUID.randomUUID().toString();

	@DynamicPropertySource
	static void configurar(DynamicPropertyRegistry registro) {
		registro.add("gastos.servicios.movimientos", () -> "http://localhost:" + MOVIMIENTOS.getAddress().getPort());
	}

	@AfterAll
	static void parar() {
		MOVIMIENTOS.stop(0);
	}

	@Test
	void lasTransaccionesImportadasSeClasificanSolasYLasManualesNo() throws Exception {
		String importada = this.registrarEnMovimientos("COMPRA LUPA SANTANDER 03 10 2026", "IMPORTACION");
		String manual = this.registrarEnMovimientos("COMPRA LUPA", "MANUAL");

		this.esperar(() -> this.clasificada(importada));
		this.mvc.perform(como(get("/transacciones/" + importada)))
				.andExpect(jsonPath("$.categoria.nombre").value("Alimentacion"))
				.andExpect(jsonPath("$.categoria.origen").value("CASCADA"))
				.andExpect(jsonPath("$.etiquetas[0].nombre").value("Supermercado"))
				.andExpect(jsonPath("$.etiquetas[0].origen").value("AUTOMATICA"));

		this.esperar(() -> this.conocida(manual));
		this.mvc.perform(como(get("/transacciones/" + manual)))
				.andExpect(jsonPath("$.categoria").doesNotExist())
				.andExpect(jsonPath("$.etiquetas").isEmpty());
	}

	@Test
	void seClasificaAManoUnaTransaccionQueAunNoHaLlegadoPorEventosConsultandoAMovimientos() throws Exception {
		String recienCreada = UUID.randomUUID().toString();
		EN_MOVIMIENTOS.put(recienCreada, "VETERINARIO KIWOKO");

		this.mvc.perform(como(put("/transacciones/" + recienCreada)).content("""
						{"nuevaCategoria":"Mascotas","nuevasEtiquetas":["Veterinario","  veterinario "]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categoria.nombre").value("Mascotas"))
				.andExpect(jsonPath("$.categoria.origen").value("MANUAL"))
				.andExpect(jsonPath("$.etiquetas.length()").value(1));
		assertThat(AUTORIZACIONES_RECIBIDAS).as("el read-through reenvía el JWT del usuario").contains("Bearer token");

		// La categoría nueva ya existe: pedirla otra vez por nombre no la duplica
		this.mvc.perform(como(put("/transacciones/" + recienCreada)).content("{\"nuevaCategoria\":\"MASCOTAS\"}"))
				.andExpect(jsonPath("$.categoria.nombre").value("Mascotas"))
				.andExpect(jsonPath("$.etiquetas").isEmpty());

		this.mvc.perform(como(put("/transacciones/" + UUID.randomUUID())).content("{}")).andExpect(status().isNotFound());
		this.mvc.perform(como(put("/transacciones/" + recienCreada)).content("{\"categoriaId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void propagaALasSimilaresUnaSolaVezYSoloAlPropietario() throws Exception {
		String base = this.registrarEnMovimientos("ZZZTIENDA PROPAGA 01 02 2024", "MANUAL");
		String similar1 = this.registrarEnMovimientos("zzztienda propaga 15 10 2023", "MANUAL");
		String similar2 = this.registrarEnMovimientos("ZZZTIENDA  PROPAGA", "MANUAL");
		this.esperar(() -> this.conocida(base) && this.conocida(similar1) && this.conocida(similar2));

		this.mvc.perform(como(put("/transacciones/" + base)).content("""
				{"nuevaCategoria":"Ocio y cultura","nuevasEtiquetas":["Videojuegos"]}""")).andExpect(status().isOk());

		this.mvc.perform(como(get("/transacciones/" + base + "/similares")))
				.andExpect(jsonPath("$.similares").value(2))
				.andExpect(jsonPath("$.claveConcepto").value("zzztienda propaga"));
		this.mvc.perform(como(post("/transacciones/" + base + "/propagacion"))).andExpect(jsonPath("$.actualizadas").value(2));
		this.mvc.perform(como(post("/transacciones/" + base + "/propagacion"))).andExpect(jsonPath("$.actualizadas").value(0));
		this.mvc.perform(como(get("/transacciones/" + similar2)))
				.andExpect(jsonPath("$.categoria.nombre").value("Ocio y cultura"))
				.andExpect(jsonPath("$.categoria.origen").value("PROPAGACION"));

		this.mvc.perform(get("/transacciones/" + base + "/similares").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
				.andExpect(status().isNotFound());
	}

	@Test
	void laAplicacionEnBloqueIgnoraLasAjenasYPuedeSustituirLasEtiquetas() throws Exception {
		String t1 = this.registrarEnMovimientos("RECIBO A", "MANUAL");
		String t2 = this.registrarEnMovimientos("RECIBO B", "MANUAL");
		this.esperar(() -> this.conocida(t1) && this.conocida(t2));

		this.mvc.perform(como(post("/aplicacion-masiva")).content("""
						{"transaccionIds":["%s","%s","%s"],"nuevasEtiquetas":["Revisar"],"modoEtiquetas":"ANADIR"}"""
						.formatted(t1, t2, UUID.randomUUID())))
				.andExpect(jsonPath("$.actualizadas").value(2))
				.andExpect(jsonPath("$.ignoradas").value(1));

		this.mvc.perform(como(post("/aplicacion-masiva")).content("""
						{"transaccionIds":["%s"],"etiquetaIds":[],"modoEtiquetas":"SUSTITUIR"}""".formatted(t1)))
				.andExpect(jsonPath("$.actualizadas").value(1));
		this.mvc.perform(como(get("/transacciones/" + t1))).andExpect(jsonPath("$.etiquetas").isEmpty());
	}

	@Test
	void laClasificacionAutomaticaRespetaLoManualYUnaTransaccionBorradaSeOlvida() throws Exception {
		String t = this.registrarEnMovimientos("FARMACIA CENTRO", "MANUAL");
		this.esperar(() -> this.conocida(t));
		this.mvc.perform(como(put("/transacciones/" + t)).content("{\"nuevaCategoria\":\"Ocio y cultura\"}")).andExpect(status().isOk());

		this.mvc.perform(como(post("/automatica")).content("{\"modo\":\"REINICIAR\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.procesadas").isNumber());
		this.mvc.perform(como(get("/transacciones/" + t)))
				.andExpect(jsonPath("$.categoria.nombre").value("Ocio y cultura"))
				.andExpect(jsonPath("$.etiquetas[0].nombre").value("Farmacia"));

		this.mvc.perform(como(post("/automatica")).content("{\"modo\":\"IMPORTACION\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("MODO_INVALIDO"));

		EN_MOVIMIENTOS.remove(t);
		this.publicar("TransaccionEliminada", t, """
				{"transaccionId":"%s","documentoId":null}""".formatted(t));
		this.esperar(() -> !this.conocida(t));
		this.mvc.perform(como(get("/transacciones/" + t))).andExpect(status().isNotFound());
	}

	private String registrarEnMovimientos(String concepto, String origen) {
		String id = UUID.randomUUID().toString();
		EN_MOVIMIENTOS.put(id, concepto);
		this.publicar("TransaccionRegistrada", id, """
				{"transaccionId":"%s","origen":"%s","cuentaId":null,"documentoId":null,"fechaContable":"2026-10-03",
				 "fechaValor":null,"concepto":"%s","nota":null,"importe":-10.00,"saldo":null,"orden":1,"ordenDocumento":1}"""
				.formatted(id, origen, concepto));
		return id;
	}

	private void publicar(String tipo, String agregado, String datos) {
		String sobre = """
				{"eventoId":"%s","tipo":"%s","contexto":"movimientos","version":1,"secuencia":1,"ocurridoEn":"2026-10-21T10:00:00Z",
				 "usuarioId":"%s","agregadoId":"%s","versionAgregado":%d,"datos":%s}"""
				.formatted(UUID.randomUUID(), tipo, this.usuario, agregado, System.nanoTime(), datos);
		try {
			this.kafka.send(Topics.MOVIMIENTOS, this.usuario, sobre).get();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private boolean conocida(String id) {
		return this.jdbc.sql("SELECT COUNT(*) FROM clasificacion_transaccion WHERE id = ?").param(UUID.fromString(id)).query(Long.class).single() > 0;
	}

	private boolean clasificada(String id) {
		return this.jdbc.sql("SELECT COUNT(*) FROM clasificacion_transaccion WHERE id = ? AND categoria_id IS NOT NULL")
				.param(UUID.fromString(id)).query(Long.class).single() > 0;
	}

	private void esperar(BooleanSupplier condicion) throws InterruptedException {
		long limite = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
		while (!condicion.getAsBoolean()) {
			assertThat(System.currentTimeMillis()).as("tiempo de espera agotado").isLessThan(limite);
			Thread.sleep(150);
		}
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion) {
		return peticion.with(jwt().jwt(j -> j.subject(this.usuario))).contentType(MediaType.APPLICATION_JSON);
	}

	/** Simula la API interna de movimientos para el read-through. */
	private static HttpServer movimientosSimulado() {
		try {
			HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			servidor.createContext("/internal/transacciones", intercambio -> {
				AUTORIZACIONES_RECIBIDAS.add(intercambio.getRequestHeaders().getFirst("Authorization"));
				String ruta = intercambio.getRequestURI().getPath();
				String cuerpo;
				int estado = 200;
				if (ruta.endsWith("/consulta")) {
					List<String> ids = JsonPath.read(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), "$.ids");
					cuerpo = ids.stream().filter(EN_MOVIMIENTOS::containsKey)
							.map(id -> "{\"id\":\"%s\",\"documentoId\":null,\"concepto\":\"%s\"}".formatted(id, EN_MOVIMIENTOS.get(id)))
							.reduce((a, b) -> a + "," + b).map(lista -> "[" + lista + "]").orElse("[]");
				} else {
					String id = ruta.substring(ruta.lastIndexOf('/') + 1);
					if (EN_MOVIMIENTOS.containsKey(id)) {
						cuerpo = "{\"id\":\"%s\",\"documentoId\":null,\"concepto\":\"%s\"}".formatted(id, EN_MOVIMIENTOS.get(id));
					} else {
						estado = 404;
						cuerpo = "{\"codigo\":\"RECURSO_NO_ENCONTRADO\"}";
					}
				}
				byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
				intercambio.getResponseHeaders().add("Content-Type", "application/json");
				intercambio.sendResponseHeaders(estado, bytes.length);
				intercambio.getResponseBody().write(bytes);
				intercambio.close();
			});
			servidor.start();
			return servidor;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
