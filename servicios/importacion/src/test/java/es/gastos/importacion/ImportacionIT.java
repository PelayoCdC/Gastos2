package es.gastos.importacion;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Importación de extractos (F7) contra la base de datos real y un movimientos simulado con un
 * HttpServer: el extracto llega interpretado, con el JWT del usuario y los errores de movimientos se
 * traducen a los mismos códigos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class ImportacionIT {

	/** Cuentas especiales del movimientos simulado. */
	private static final String CUENTA_AJENA = "00000000-0000-0000-0000-00000000a1e1";

	private static final String CUENTA_RECHAZADA = "00000000-0000-0000-0000-0000000000ba";

	private static final String CUENTA_ROTA = "00000000-0000-0000-0000-000000000500";

	private static final List<String> EXTRACTOS_RECIBIDOS = new CopyOnWriteArrayList<>();

	private static final List<String> AUTORIZACIONES_RECIBIDAS = new CopyOnWriteArrayList<>();

	private static final HttpServer MOVIMIENTOS = movimientosSimulado();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@LocalServerPort
	private int puerto;

	/** Solo para las peticiones HTTP reales (subida demasiado grande); MockMvc usa {@code jwt()}. */
	@MockitoBean
	private JwtDecoder decodificador;

	private final String usuario = UUID.randomUUID().toString();

	@DynamicPropertySource
	static void configurar(DynamicPropertyRegistry registro) {
		registro.add("gastos.servicios.movimientos", () -> "http://localhost:" + MOVIMIENTOS.getAddress().getPort());
	}

	@AfterAll
	static void parar() {
		MOVIMIENTOS.stop(0);
	}

	@BeforeEach
	void prepararToken() {
		when(this.decodificador.decode(anyString())).thenReturn(Jwt.withTokenValue("real").header("alg", "RS256")
				.subject(this.usuario).build());
	}

	@Test
	void gestionaFormatosConNombreUnicoYAnunciaLosCambios() throws Exception {
		String id = this.crearFormato("Unicaja xlsx");

		this.mvc.perform(como(post("/formatos")).content(formato(" UNICAJA  XLSX ")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NOMBRE_DUPLICADO"))
				.andExpect(header().doesNotExist("Gastos-Secuencia"));
		this.mvc.perform(como(post("/formatos")).content("{\"nombre\":\"Sin columnas\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("MAPEO_INCOMPLETO"));
		this.mvc.perform(como(post("/formatos")).content(formato("Raro").replace("dd/MM/yyyy", "dd/MM/yyyy{")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FORMATO_FECHA_INVALIDO"));

		this.mvc.perform(como(put("/formatos/" + id)).content(formato("Unicaja").replace("\"filaInicio\":2", "\"filaInicio\":3")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filaInicio").value(3));
		this.mvc.perform(como(get("/formatos")))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].nombre").value("Unicaja"));
		this.mvc.perform(get("/formatos/" + id).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
				.andExpect(status().isNotFound());

		this.mvc.perform(como(delete("/formatos/" + id))).andExpect(status().isNoContent());
		this.mvc.perform(como(get("/formatos/" + id))).andExpect(status().isNotFound());

		assertThat(this.jdbc.sql("SELECT tipo FROM outbox WHERE agregado_id = ? ORDER BY id").param(UUID.fromString(id))
				.query(String.class).list())
				.containsExactly("FormatoBancoGuardado", "FormatoBancoGuardado", "FormatoBancoEliminado");
	}

	@Test
	void previsualizaLasPrimerasFilasDeUnCsv() throws Exception {
		String csv = "Fecha;Concepto;Importe\n01/07/2026;\"CON; SEPARADOR\";-12,34\n";

		this.mvc.perform(como(multipart("/previsualizaciones").file(archivo("extracto.csv", csv.getBytes(StandardCharsets.UTF_8)))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombreArchivo").value("extracto.csv"))
				.andExpect(jsonPath("$.totalFilas").value(2))
				.andExpect(jsonPath("$.filas[1][1]").value("CON; SEPARADOR"));

		this.mvc.perform(como(multipart("/previsualizaciones").file(archivo("extracto.xlsx", "no es un excel".getBytes()))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FICHERO_ILEGIBLE"));
	}

	@Test
	void importaUnExcelEntregandoAMovimientosLasLineasInterpretadasConElJwtDelUsuario() throws Exception {
		String formato = this.crearFormato("Banco " + UUID.randomUUID());
		String cuenta = UUID.randomUUID().toString();

		this.mvc.perform(como(importar(formato, cuenta).file(archivo("extracto.xlsx", excel()))).param("nombre", "Julio"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.importadas").value(2))
				.andExpect(jsonPath("$.nombreArchivo").value("extracto.xlsx"))
				.andExpect(jsonPath("$.errores.length()").value(1))
				.andExpect(jsonPath("$.errores[0].mensaje").value("Fila 4: fecha con formato incorrecto: '31/02/2026'"))
				.andExpect(jsonPath("$.duplicados").isEmpty())
				.andExpect(header().string("Gastos-Secuencia", "movimientos=4242"));

		assertThat(AUTORIZACIONES_RECIBIDAS).contains("Bearer token");
		String extracto = EXTRACTOS_RECIBIDOS.getLast();
		assertThat(JsonPath.<String>read(extracto, "$.cuentaId")).isEqualTo(cuenta);
		assertThat(JsonPath.<String>read(extracto, "$.nombre")).isEqualTo("Julio");
		assertThat(JsonPath.<List<String>>read(extracto, "$.lineas[*].concepto")).containsExactly("FARMACIA TEST S.L.", "SUPERMERCADO TEST");
		assertThat(JsonPath.<List<String>>read(extracto, "$.lineas[*].fechaContable")).containsExactly("2026-07-01", "2026-07-02");
		assertThat(JsonPath.<List<Number>>read(extracto, "$.lineas[*].importe")).extracting(Number::doubleValue)
				.containsExactly(-12.34, -45.6);
		assertThat(JsonPath.<List<Integer>>read(extracto, "$.lineas[*].fila")).containsExactly(2, 3);
	}

	@Test
	void traduceLasRespuestasDeMovimientosALosMismosCodigos() throws Exception {
		String formato = this.crearFormato("Banco " + UUID.randomUUID());
		byte[] csv = "Fecha;Concepto;Importe\n01/07/2026;CAFE;-1,50\n".getBytes(StandardCharsets.UTF_8);

		this.mvc.perform(como(importar(formato, CUENTA_AJENA).file(archivo("e.csv", csv))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
		this.mvc.perform(como(importar(formato, CUENTA_RECHAZADA).file(archivo("e.csv", csv))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LINEA_INVALIDA"))
				.andExpect(jsonPath("$.detail").value("La fila 2 no trae fecha contable o importe"));
		this.mvc.perform(como(importar(formato, CUENTA_ROTA).file(archivo("e.csv", csv))))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));

		this.mvc.perform(como(importar(UUID.randomUUID().toString(), UUID.randomUUID().toString()).file(archivo("e.csv", csv))))
				.andExpect(status().isNotFound());
		this.mvc.perform(como(multipart("/importaciones").file(archivo("e.csv", csv))).param("formatoId", formato))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rechazaFicherosDeMasDeDiezMegasCon413() {
		// Cuerpo multipart montado a mano para enviarlo con Content-Length, como un navegador o curl
		String separador = "limite" + UUID.randomUUID();
		ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
		cuerpo.writeBytes(("--" + separador + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"enorme.csv\"\r\n"
				+ "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		cuerpo.writeBytes(new byte[10 * 1024 * 1024 + 100 * 1024]);
		cuerpo.writeBytes(("\r\n--" + separador + "--\r\n").getBytes(StandardCharsets.US_ASCII));

		ResponseEntity<String> respuesta = RestClient.create().post()
				.uri("http://localhost:" + this.puerto + "/previsualizaciones")
				.header("Authorization", "Bearer real")
				.contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + separador))
				.body(cuerpo.toByteArray())
				.retrieve()
				.onStatus(estado -> true, (peticion, r) -> {
				})
				.toEntity(String.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(respuesta.getBody()).contains("FICHERO_DEMASIADO_GRANDE");
	}

	private String crearFormato(String nombre) throws Exception {
		String respuesta = this.mvc.perform(como(post("/formatos")).content(formato(nombre)))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(header().string("Gastos-Secuencia", matchesPattern("importacion=\\d+")))
				.andExpect(jsonPath("$.formatoFecha").value("dd/MM/yyyy"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.id");
	}

	/** Fecha, Concepto, Importe desde la fila 2. */
	private static String formato(String nombre) {
		return """
				{"nombre":"%s","filaInicio":2,"columnaFechaContable":0,"columnaConcepto":1,"columnaImporte":2,"formatoFecha":"dd/MM/yyyy"}"""
				.formatted(nombre);
	}

	private static MockMultipartHttpServletRequestBuilder importar(String formatoId, String cuentaId) {
		MockMultipartHttpServletRequestBuilder peticion = multipart("/importaciones");
		peticion.param("formatoId", formatoId).param("cuentaId", cuentaId);
		return peticion;
	}

	private static MockMultipartFile archivo(String nombre, byte[] contenido) {
		return new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido);
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion) {
		return peticion.with(jwt().jwt(j -> j.subject(this.usuario))).contentType(MediaType.APPLICATION_JSON);
	}

	private MockMultipartHttpServletRequestBuilder como(MockMultipartHttpServletRequestBuilder peticion) {
		return peticion.with(jwt().jwt(j -> j.subject(this.usuario)));
	}

	/** Dos movimientos válidos (importe numérico y como texto) y uno con una fecha imposible. */
	private static byte[] excel() throws IOException {
		try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Sheet hoja = libro.createSheet("Movimientos");
			Row cabecera = hoja.createRow(0);
			cabecera.createCell(0).setCellValue("Fecha");
			cabecera.createCell(1).setCellValue("Concepto");
			cabecera.createCell(2).setCellValue("Importe");
			Row fila1 = hoja.createRow(1);
			fila1.createCell(0).setCellValue("01/07/2026");
			fila1.createCell(1).setCellValue("FARMACIA TEST S.L.");
			fila1.createCell(2).setCellValue(-12.34);
			Row fila2 = hoja.createRow(2);
			fila2.createCell(0).setCellValue("02/07/2026");
			fila2.createCell(1).setCellValue("SUPERMERCADO TEST");
			fila2.createCell(2).setCellValue("-45,60");
			Row fila3 = hoja.createRow(3);
			fila3.createCell(0).setCellValue("31/02/2026");
			fila3.createCell(1).setCellValue("IMPOSIBLE");
			fila3.createCell(2).setCellValue(-1);
			libro.write(salida);
			return salida.toByteArray();
		}
	}

	/** Simula {@code POST /internal/extractos} de movimientos. */
	private static HttpServer movimientosSimulado() {
		try {
			HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			servidor.createContext("/internal/extractos", intercambio -> {
				AUTORIZACIONES_RECIBIDAS.add(intercambio.getRequestHeaders().getFirst("Authorization"));
				String peticion = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				EXTRACTOS_RECIBIDOS.add(peticion);
				String cuenta = JsonPath.read(peticion, "$.cuentaId");
				int estado;
				String cuerpo;
				switch (cuenta) {
					case CUENTA_AJENA -> {
						estado = 404;
						cuerpo = "{\"codigo\":\"RECURSO_NO_ENCONTRADO\",\"detail\":\"No existe la cuenta\"}";
					}
					case CUENTA_RECHAZADA -> {
						estado = 400;
						cuerpo = "{\"codigo\":\"LINEA_INVALIDA\",\"detail\":\"La fila 2 no trae fecha contable o importe\"}";
					}
					case CUENTA_ROTA -> {
						estado = 500;
						cuerpo = "{}";
					}
					default -> {
						estado = 201;
						cuerpo = """
								{"documentoId":"%s","nombreDocumento":"Julio","nombreArchivo":"%s","importadas":%d,"duplicados":[]}"""
								.formatted(JsonPath.<String>read(peticion, "$.extractoId"), JsonPath.<String>read(peticion, "$.nombreArchivo"),
										JsonPath.<List<?>>read(peticion, "$.lineas").size());
					}
				}
				byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
				intercambio.getResponseHeaders().add("Content-Type", estado < 300 ? "application/json" : "application/problem+json");
				if (estado < 300) {
					intercambio.getResponseHeaders().add("Gastos-Secuencia", "movimientos=4242");
				}
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
