package es.gastos.consultas;

import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import es.gastos.consultas.infraestructura.kafka.PreparacionProyeccion;
import es.gastos.contratos.Topics;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proyecciones de consultas (F8) contra MariaDB y Kafka reales: los eventos se publican en los topics
 * tal y como lo harían movimientos y clasificacion, y se comprueba lo que devuelve la API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class ConsultasIT {

	private static final AtomicLong SECUENCIA = new AtomicLong(1000);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private KafkaTemplate<String, String> kafka;

	@Autowired
	private JsonMapper json;

	@Autowired
	private KafkaListenerEndpointRegistry consumidores;

	@Autowired
	private PreparacionProyeccion preparacion;

	private final UUID usuario = UUID.randomUUID();

	private final UUID cuenta = UUID.randomUUID();

	private final UUID documento = UUID.randomUUID();

	private final UUID categoria = UUID.randomUUID();

	private final UUID etiqueta = UUID.randomUUID();

	private final UUID compra = UUID.randomUUID();

	private final UUID nada = UUID.randomUUID();

	@Test
	void filtraYOrdenaComoElBackAntiguo() throws Exception {
		this.extractoDeDosFilas();

		// Categoría pedida: solo la etiquetada, con su categoría heredada en cascada
		this.mvc.perform(como(get("/transacciones").param("categoriaId", this.categoria.toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].concepto").value("ZZZCASCADA COMPRA"))
				.andExpect(jsonPath("$.contenido[0].categoria.nombre").value("CatCascada"))
				.andExpect(jsonPath("$.contenido[0].etiquetas[0].nombre").value("EtiCascada"))
				.andExpect(jsonPath("$.contenido[0].cuenta.nombre").value("Cuenta IT"))
				.andExpect(jsonPath("$.contenido[0].documento.nombre").value("Julio"));
		// Sin etiqueta; y lista de etiquetas O sin etiqueta: las dos
		this.mvc.perform(como(get("/transacciones").param("sinEtiqueta", "true").param("concepto", "zzz")))
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].concepto").value("ZZZNADA ESPECIAL"));
		this.mvc.perform(como(get("/transacciones").param("sinEtiqueta", "true").param("etiquetaId", this.etiqueta.toString())))
				.andExpect(jsonPath("$.totalElementos").value(2));
		// Sin categoría; y lista de categorías O sin categoría: las dos
		this.mvc.perform(como(get("/transacciones").param("sinCategoria", "true")))
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].concepto").value("ZZZNADA ESPECIAL"));
		this.mvc.perform(como(get("/transacciones").param("sinCategoria", "true").param("categoriaId", this.categoria.toString())))
				.andExpect(jsonPath("$.totalElementos").value(2));

		// Concepto sin tildes ni mayúsculas, rango de importes y de fechas
		this.mvc.perform(como(get("/transacciones").param("concepto", "Especíal")))
				.andExpect(jsonPath("$.totalElementos").value(1));
		this.mvc.perform(como(get("/transacciones").param("concepto", "100%")))
				.andExpect(jsonPath("$.totalElementos").value(0));
		this.mvc.perform(como(get("/transacciones").param("importeMax", "-15")))
				.andExpect(jsonPath("$.contenido[0].importe").value(-20.0));
		this.mvc.perform(como(get("/transacciones").param("desde", "2026-07-02").param("hasta", "2026-07-02")))
				.andExpect(jsonPath("$.totalElementos").value(1));

		// Orden por defecto (fecha descendente), por importe, y paginación estable
		this.mvc.perform(como(get("/transacciones")))
				.andExpect(jsonPath("$.contenido[0].concepto").value("ZZZNADA ESPECIAL"));
		this.mvc.perform(como(get("/transacciones").param("orden", "importe").param("direccion", "ASC")))
				.andExpect(jsonPath("$.contenido[0].importe").value(-20.0));
		this.mvc.perform(como(get("/transacciones").param("tamano", "1").param("pagina", "1")))
				.andExpect(jsonPath("$.contenido[0].concepto").value("ZZZCASCADA COMPRA"))
				.andExpect(jsonPath("$.totalPaginas").value(2));
		this.mvc.perform(como(get("/transacciones").param("orden", "password")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("ORDEN_INVALIDO"));

		// Detalle; y otro usuario no ve nada
		this.mvc.perform(como(get("/transacciones/" + this.compra)))
				.andExpect(jsonPath("$.orden").value(2))
				.andExpect(jsonPath("$.saldo").value(90.0));
		this.mvc.perform(get("/transacciones/" + this.compra).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
				.andExpect(status().isNotFound());
		this.mvc.perform(get("/transacciones").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
				.andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	void esperaALaProyeccionCuandoSeLePideConGastosEsperar() throws Exception {
		long ultima = this.extractoDeDosFilas();

		this.mvc.perform(como(get("/transacciones")).header("Gastos-Esperar", "movimientos=" + ultima + ",identidad=5"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Gastos-Desfase"));

		long inicio = System.nanoTime();
		this.mvc.perform(como(get("/transacciones")).header("Gastos-Esperar", "clasificacion=" + (ultima + 1_000_000)))
				.andExpect(status().isOk())
				.andExpect(header().string("Gastos-Desfase", "true"));
		assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isBetween(Duration.ofMillis(2900), Duration.ofSeconds(10));

		this.mvc.perform(como(get("/transacciones")).header("Gastos-Esperar", "movimientos:1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CABECERA_INVALIDA"));
	}

	@Test
	void aguantaEventosDesordenadosRepetidosYTardios() throws Exception {
		UUID t = UUID.randomUUID();
		this.categoriaYEtiqueta();

		// La clasificación llega antes que el registro (otro topic, sin orden entre ambos)
		this.clasificacion("TransaccionClasificada", t, 1, datos("transaccionId", t, "categoriaId", this.categoria,
				"etiquetaIds", List.of(this.etiqueta), "motivo", "AUTOMATICA"));
		this.movimientos("TransaccionRegistrada", t, 1, registrada(t, null, "BAR PACO", "-3.00", "2026-07-03", 1));
		// Corregida y, después, una entrega repetida del registro antiguo
		this.movimientos("TransaccionCorregida", t, 2, datos("transaccionId", t, "cuentaId", null, "fechaContable", "2026-07-03",
				"fechaValor", null, "concepto", "BAR PACO (CORREGIDO)", "nota", "con nota", "importe", -4.00));
		long ultima = this.movimientos("TransaccionRegistrada", t, 1, registrada(t, null, "BAR PACO", "-3.00", "2026-07-03", 1));
		this.esperarPosicion("movimientos", ultima);

		this.mvc.perform(como(get("/transacciones/" + t)))
				.andExpect(jsonPath("$.concepto").value("BAR PACO (CORREGIDO)"))
				.andExpect(jsonPath("$.nota").value("con nota"))
				.andExpect(jsonPath("$.importe").value(-4.0))
				.andExpect(jsonPath("$.categoria.nombre").value("CatCascada"));

		// Se borra y después llega tarde una clasificación más nueva: no la resucita
		long eliminada = this.movimientos("TransaccionEliminada", t, 3, datos("transaccionId", t, "documentoId", null));
		long tardia = this.clasificacion("TransaccionClasificada", t, 2, datos("transaccionId", t, "categoriaId", null,
				"etiquetaIds", List.of(), "motivo", "MANUAL"));
		this.esperarPosicion("movimientos", eliminada);
		this.esperarPosicion("clasificacion", tardia);
		this.mvc.perform(como(get("/transacciones/" + t))).andExpect(status().isNotFound());
	}

	@Test
	void losEventosDeConjuntoAfectanATodasLasTransaccionesDelDocumentoOCatalogo() throws Exception {
		this.extractoDeDosFilas();
		UUID otraCuenta = UUID.randomUUID();
		this.movimientos("CuentaGuardada", otraCuenta, 1, datos("cuentaId", otraCuenta, "nombre", "Ahorro", "banco", null,
				"descripcion", null));

		long secuencia = this.movimientos("DocumentoReasignado", this.documento, 2, datos("documentoId", this.documento,
				"cuentaIdAnterior", this.cuenta, "cuentaIdNueva", otraCuenta));
		this.esperarPosicion("movimientos", secuencia);
		this.mvc.perform(como(get("/transacciones").param("cuentaId", otraCuenta.toString())))
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[0].cuenta.nombre").value("Ahorro"));

		this.clasificacion("CategoriaEliminada", this.categoria, 2, datos("categoriaId", this.categoria));
		secuencia = this.clasificacion("EtiquetaEliminada", this.etiqueta, 2, datos("etiquetaId", this.etiqueta));
		this.esperarPosicion("clasificacion", secuencia);
		this.mvc.perform(como(get("/transacciones").param("sinCategoria", "true").param("sinEtiqueta", "true")))
				.andExpect(jsonPath("$.totalElementos").value(2));

		secuencia = this.movimientos("DocumentoEliminado", this.documento, 3, datos("documentoId", this.documento));
		this.esperarPosicion("movimientos", secuencia);
		this.mvc.perform(como(get("/transacciones"))).andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	void exportaUnExcelConLasColumnasDelBackAntiguoYFechasEImportesReales() throws Exception {
		this.extractoDeDosFilas();

		byte[] excel = this.mvc.perform(como(get("/transacciones/exportacion").param("orden", "fechaContable").param("direccion", "ASC")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.andExpect(header().string("Content-Disposition", containsString("transacciones-")))
				.andReturn().getResponse().getContentAsByteArray();

		try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			Sheet hoja = libro.getSheetAt(0);
			Row cabecera = hoja.getRow(0);
			assertThat(List.of(cabecera.getCell(0).getStringCellValue(), cabecera.getCell(1).getStringCellValue(),
					cabecera.getCell(2).getStringCellValue(), cabecera.getCell(3).getStringCellValue(), cabecera.getCell(4).getStringCellValue(),
					cabecera.getCell(5).getStringCellValue(), cabecera.getCell(6).getStringCellValue()))
					.containsExactly("Fecha", "Concepto", "Cuenta", "Categoria", "Etiquetas", "Importe", "Saldo");
			Row compra = hoja.getRow(1);
			assertThat(compra.getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 1));
			assertThat(compra.getCell(1).getStringCellValue()).isEqualTo("ZZZCASCADA COMPRA");
			assertThat(compra.getCell(3).getStringCellValue()).isEqualTo("CatCascada");
			assertThat(compra.getCell(4).getStringCellValue()).isEqualTo("EtiCascada");
			assertThat(compra.getCell(5).getNumericCellValue()).isEqualTo(-10.0);
			assertThat(compra.getCell(6).getNumericCellValue()).isEqualTo(90.0);
			assertThat(hoja.getRow(2).getCell(6)).as("sin saldo, celda vacía").isNull();
			assertThat(hoja.getLastRowNum()).isEqualTo(2);
		}
	}

	@Test
	void losInformesSalenDeLoProyectadoYUnaReconstruccionLosDejaIguales() throws Exception {
		this.extractoDeDosFilas();
		UUID bizum = UUID.randomUUID();
		UUID recibido = UUID.randomUUID();
		UUID cena = UUID.randomUUID();
		this.clasificacion("EtiquetaGuardada", bizum, 1, datos("etiquetaId", bizum, "nombre", "Bizum", "descripcion", null,
				"ocurrencias", List.of("bizum"), "categoriaId", null, "prioridad", 2));
		this.movimientos("TransaccionRegistrada", recibido, 1, registrada(recibido, null, "JESUS RUIZ", "50.00", "2026-08-01", 1));
		long movimientos = this.movimientos("TransaccionRegistrada", cena, 1, registrada(cena, null, "BIZUM: Cena", "-15.00", "2026-08-02", 2));
		long clasificacion = this.clasificacion("TransaccionClasificada", recibido, 1, datos("transaccionId", recibido, "categoriaId", null,
				"etiquetaIds", List.of(bizum), "motivo", "AUTOMATICA"));
		this.esperarPosicion("movimientos", movimientos);
		this.esperarPosicion("clasificacion", clasificacion);

		this.mvc.perform(como(get("/informes/resumen-mensual")))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].mes").value(7))
				.andExpect(jsonPath("$[0].gastos").value(30.0))
				.andExpect(jsonPath("$[1].ingresos").value(50.0))
				.andExpect(jsonPath("$[1].balance").value(35.0));
		this.mvc.perform(como(get("/informes/gastos-por-categoria").param("desde", "2026-07-01").param("hasta", "2026-07-31")))
				.andExpect(jsonPath("$[0].categoria").doesNotExist())
				.andExpect(jsonPath("$[0].total").value(20.0))
				.andExpect(jsonPath("$[1].categoria.nombre").value("CatCascada"));
		this.mvc.perform(como(get("/informes/bizum")))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.contacto == 'jesus ruiz')].recibido").value(50.0))
				.andExpect(jsonPath("$[?(@.contacto == 'cena')].enviado").value(15.0));
		this.mvc.perform(como(get("/informes/top-conceptos").param("limite", "0")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"));
		String[] antes = this.informes();

		// Reconstrucción en caliente: consumidor parado, tablas vacías con la versión 2 y vuelta a leer los topics
		MessageListenerContainer consumidor = this.consumidores.getListenerContainer("proyecciones");
		consumidor.stop();
		this.preparacion.preparar(2);
		this.mvc.perform(como(get("/informes/resumen-mensual"))).andExpect(jsonPath("$.length()").value(0));
		consumidor.start();
		this.esperarPosicion("movimientos", movimientos);
		this.esperarPosicion("clasificacion", clasificacion);

		assertThat(this.informes()).isEqualTo(antes);
		this.mvc.perform(get("/actuator/health/proyeccion"))
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.details.grupo").value("consultas-v1"))
				.andExpect(jsonPath("$.details.retraso").isNumber());
	}

	/** Todos los informes del usuario como JSON, para comparar antes y después de reconstruir. */
	private String[] informes() throws Exception {
		List<String> rutas = List.of("resumen-mensual", "evolucion-saldo", "gastos-por-categoria", "gastos-por-etiqueta", "gasto-diario",
				"gastos-mensuales-por-categoria", "top-conceptos", "recurrentes", "bizum");
		String[] respuestas = new String[rutas.size()];
		for (int i = 0; i < rutas.size(); i++) {
			respuestas[i] = this.mvc.perform(como(get("/informes/" + rutas.get(i)))).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
		}
		return respuestas;
	}

	/** Cuenta, documento y dos transacciones; la primera clasificada con categoría y etiqueta. Devuelve la última secuencia. */
	private long extractoDeDosFilas() throws Exception {
		this.categoriaYEtiqueta();
		this.movimientos("CuentaGuardada", this.cuenta, 1, datos("cuentaId", this.cuenta, "nombre", "Cuenta IT", "banco", "Banco",
				"descripcion", null));
		this.movimientos("DocumentoRegistrado", this.documento, 1, datos("documentoId", this.documento, "cuentaId", this.cuenta,
				"nombre", "Julio", "nombreArchivo", "julio.csv", "fecha", "2026-07-05"));
		Map<String, Object> primera = registrada(this.compra, this.documento, "ZZZCASCADA COMPRA", "-10.00", "2026-07-01", 2);
		primera.put("saldo", 90.00);
		this.movimientos("TransaccionRegistrada", this.compra, 1, primera);
		long ultima = this.movimientos("TransaccionRegistrada", this.nada, 1,
				registrada(this.nada, this.documento, "ZZZNADA ESPECIAL", "-20.00", "2026-07-02", 3));
		long clasificada = this.clasificacion("TransaccionClasificada", this.compra, 1, datos("transaccionId", this.compra,
				"categoriaId", this.categoria, "etiquetaIds", List.of(this.etiqueta), "motivo", "AUTOMATICA"));
		this.esperarPosicion("movimientos", ultima);
		this.esperarPosicion("clasificacion", clasificada);
		return ultima;
	}

	private void categoriaYEtiqueta() {
		this.clasificacion("CategoriaGuardada", this.categoria, 1, datos("categoriaId", this.categoria, "nombre", "CatCascada",
				"color", "#123456", "descripcion", null, "ocurrencias", List.of(), "prioridad", 1));
		this.clasificacion("EtiquetaGuardada", this.etiqueta, 1, datos("etiquetaId", this.etiqueta, "nombre", "EtiCascada",
				"descripcion", null, "ocurrencias", List.of("zzzcascada"), "categoriaId", this.categoria, "prioridad", 1));
	}

	private Map<String, Object> registrada(UUID id, UUID documentoId, String concepto, String importe, String fecha, int orden) {
		return datos("transaccionId", id, "origen", "IMPORTACION", "cuentaId", documentoId == null ? null : this.cuenta,
				"documentoId", documentoId, "fechaContable", fecha, "fechaValor", null, "concepto", concepto, "nota", null,
				"importe", Double.parseDouble(importe), "saldo", null, "orden", orden, "ordenDocumento", orden);
	}

	private long movimientos(String tipo, UUID agregado, long version, Map<String, Object> datos) {
		return this.publicar(Topics.MOVIMIENTOS, "movimientos", tipo, agregado, version, datos);
	}

	private long clasificacion(String tipo, UUID agregado, long version, Map<String, Object> datos) {
		return this.publicar(Topics.CLASIFICACION, "clasificacion", tipo, agregado, version, datos);
	}

	private long publicar(String topic, String contexto, String tipo, UUID agregado, long version, Map<String, Object> datos) {
		long secuencia = SECUENCIA.incrementAndGet();
		Map<String, Object> sobre = datos("eventoId", UUID.randomUUID(), "tipo", tipo, "contexto", contexto, "version", 1,
				"secuencia", secuencia, "ocurridoEn", "2026-10-22T10:00:00Z", "usuarioId", this.usuario, "agregadoId", agregado,
				"versionAgregado", version, "datos", datos);
		try {
			this.kafka.send(topic, this.usuario.toString(), this.json.writeValueAsString(sobre)).get();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return secuencia;
	}

	private void esperarPosicion(String contexto, long secuencia) throws InterruptedException {
		esperar(() -> this.jdbc.sql("SELECT COUNT(*) FROM proyeccion_posicion WHERE usuario_id = ? AND contexto = ? AND secuencia >= ?")
				.params(this.usuario, contexto, secuencia).query(Long.class).single() > 0);
	}

	private static void esperar(BooleanSupplier condicion) throws InterruptedException {
		long limite = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
		while (!condicion.getAsBoolean()) {
			assertThat(System.currentTimeMillis()).as("tiempo de espera agotado").isLessThan(limite);
			Thread.sleep(100);
		}
	}

	/** Mapa con nulos permitidos, en el orden dado (clave, valor, clave, valor...). */
	private static Map<String, Object> datos(Object... claveValor) {
		Map<String, Object> mapa = new LinkedHashMap<>();
		for (int i = 0; i < claveValor.length; i += 2) {
			mapa.put((String) claveValor[i], claveValor[i + 1]);
		}
		return mapa;
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion) {
		return peticion.with(jwt().jwt(j -> j.subject(this.usuario.toString())));
	}
}
