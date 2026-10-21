package es.gastos.clasificacion;

import com.jayway.jsonpath.JsonPath;
import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import es.gastos.contratos.Topics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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

/** Catálogo (F5): siembra, nombres únicos, prioridad, borrados con arrastre y definiciones. */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class CatalogoIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private KafkaTemplate<String, String> kafka;

	private final String usuario = UUID.randomUUID().toString();

	@Test
	void unUsuarioNuevoVeElCatalogoInicialEnSuOrdenDePrioridad() throws Exception {
		this.mvc.perform(como(get("/categorias")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(12))
				.andExpect(jsonPath("$[0].nombre").value("Alimentacion"))
				.andExpect(jsonPath("$[0].prioridad").value(1))
				.andExpect(jsonPath("$[11].nombre").value("Ingresos"))
				.andExpect(jsonPath("$[2].ocurrencias[1]").value("bar "));

		String etiquetas = this.mvc.perform(como(get("/etiquetas")))
				.andExpect(jsonPath("$.length()").value(23))
				.andReturn().getResponse().getContentAsString();
		List<String> nombres = JsonPath.read(etiquetas, "$[*].nombre");
		assertThat(nombres.indexOf("Suscripciones")).as("Suscripciones va antes que Compras online").isLessThan(nombres.indexOf("Compras online"));
		assertThat(JsonPath.<List<Object>>read(etiquetas, "$[?(@.categoria != null)]")).hasSize(20);

		this.mvc.perform(como(get("/etiquetas")).param("sinCategoria", "true"))
				.andExpect(jsonPath("$[*].nombre").value(org.hamcrest.Matchers.contains("Bizum", "Transferencias", "Efectivo")));
	}

	@Test
	void losNombresSonUnicosSinDistinguirMayusculasTildesNiEspacios() throws Exception {
		this.mvc.perform(como(post("/categorias")).content("{\"nombre\":\"  SALUD \"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NOMBRE_DUPLICADO"));
		this.mvc.perform(como(post("/categorias")).content("{\"nombre\":\"Mascotas\",\"color\":\"#AABBCC\",\"ocurrencias\":[\"kiwoko\",\"veterinario\"]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.prioridad").value(13))
				.andExpect(jsonPath("$.color").value("#aabbcc"));
		// Una etiqueta sí puede llamarse como una categoría (el catálogo inicial ya lo hace)
		this.mvc.perform(como(post("/etiquetas")).content("{\"nombre\":\"Mascotas\"}")).andExpect(status().isCreated());
		// Y su cascada tiene que ser una categoría del usuario
		this.mvc.perform(como(post("/etiquetas")).content("{\"nombre\":\"Otra\",\"categoriaId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void borrarUnaCategoriaUsadaEnCascadaLaQuitaDeLasEtiquetasEnVezDeFallar() throws Exception {
		String salud = this.idPorNombre("/categorias", "Salud");

		this.mvc.perform(como(delete("/categorias/" + salud))).andExpect(status().isNoContent());

		this.mvc.perform(como(get("/etiquetas")))
				.andExpect(jsonPath("$[?(@.nombre == 'Farmacia')].categoria").value(org.hamcrest.Matchers.contains((Object) null)));
		List<String> eventos = this.jdbc.sql("SELECT tipo FROM outbox WHERE usuario_id = ? AND id > (SELECT MAX(id) - 2 FROM outbox WHERE usuario_id = ?) ORDER BY id")
				.param(UUID.fromString(this.usuario)).param(UUID.fromString(this.usuario)).query(String.class).list();
		assertThat(eventos).containsExactly("EtiquetaGuardada", "CategoriaEliminada");
	}

	@Test
	void reordenarExigeLaListaCompletaYDejaElOrdenPedido() throws Exception {
		String categorias = this.mvc.perform(como(get("/categorias"))).andReturn().getResponse().getContentAsString();
		List<String> ids = new ArrayList<>(JsonPath.read(categorias, "$[*].id"));

		this.mvc.perform(como(put("/categorias/orden")).content(orden(ids.subList(0, 3))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("ORDEN_INVALIDO"));

		Collections.reverse(ids);
		this.mvc.perform(como(put("/categorias/orden")).content(orden(ids)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Ingresos"))
				.andExpect(jsonPath("$[0].prioridad").value(1));
	}

	@Test
	void lasDefinicionesSeExportanEImportanPorNombre() throws Exception {
		this.mvc.perform(como(get("/definiciones")))
				.andExpect(jsonPath("$.categorias.length()").value(12))
				.andExpect(jsonPath("$.etiquetas[?(@.nombre == 'Farmacia')].categoria").value(org.hamcrest.Matchers.contains("Salud")));

		this.mvc.perform(como(post("/definiciones")).content("""
						{"categorias":[{"nombre":"salud","color":"#000000","ocurrencias":["farmacia","optica"]}],
						 "etiquetas":[{"nombre":"Gimnasio","ocurrencias":["basic fit"],"categoria":"Deporte"}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categorias.actualizadas").value(1))
				.andExpect(jsonPath("$.categorias.creadas").value(1))
				.andExpect(jsonPath("$.etiquetas.creadas").value(1));

		this.mvc.perform(como(get("/categorias")))
				.andExpect(jsonPath("$[?(@.nombre == 'Salud')].color").value(org.hamcrest.Matchers.contains("#000000")))
				.andExpect(jsonPath("$[?(@.nombre == 'Deporte')]").exists());
	}

	@Test
	void elEventoUsuarioRegistradoSiembraElCatalogoUnaSolaVez() throws Exception {
		UUID nuevo = UUID.randomUUID();
		String sobre = """
				{"eventoId":"%s","tipo":"UsuarioRegistrado","contexto":"identidad","version":1,"secuencia":1,
				 "ocurridoEn":"2026-10-21T10:00:00Z","usuarioId":"%s","agregadoId":"%s","versionAgregado":1,
				 "datos":{"usuarioId":"%s","email":"x@ejemplo.es","nombre":"X"}}""".formatted(UUID.randomUUID(), nuevo, nuevo, nuevo);

		this.kafka.send(Topics.IDENTIDAD, nuevo.toString(), sobre).get();
		this.kafka.send(Topics.IDENTIDAD, nuevo.toString(), sobre).get();

		long limite = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
		while (this.contarCategorias(nuevo) < 12 && System.currentTimeMillis() < limite) {
			Thread.sleep(200);
		}
		Thread.sleep(1000);
		assertThat(this.contarCategorias(nuevo)).isEqualTo(12);
		assertThat(this.jdbc.sql("SELECT COUNT(*) FROM inbox WHERE consumidor = 'clasificacion.siembra' AND evento_id = ?")
				.param(UUID.fromString(JsonPath.read(sobre, "$.eventoId"))).query(Long.class).single()).isEqualTo(1L);
	}

	private long contarCategorias(UUID usuario) {
		return this.jdbc.sql("SELECT COUNT(*) FROM categoria WHERE usuario_id = ?").param(usuario).query(Long.class).single();
	}

	private String idPorNombre(String recurso, String nombre) throws Exception {
		String lista = this.mvc.perform(como(get(recurso))).andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(lista, "$[?(@.nombre == '" + nombre + "')].id");
		return ids.getFirst();
	}

	private static String orden(List<String> ids) {
		return "{\"ids\":[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]}";
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion) {
		return peticion.with(jwt().jwt(j -> j.subject(this.usuario))).contentType(MediaType.APPLICATION_JSON);
	}
}
