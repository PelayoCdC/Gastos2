package es.gastos.clasificacion;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parte de clasificacion de la restauración de un respaldo (F10, docs/adr/0020). */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class RespaldoIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private final String usuario = UUID.randomUUID().toString();

	private final String ocio = UUID.randomUUID().toString();

	private final String cine = UUID.randomUUID().toString();

	private final String transaccion = UUID.randomUUID().toString();

	@Test
	void sustituyeElCatalogoSembradoPorElDelRespaldoYLaSiembraNoVuelve() throws Exception {
		// Un usuario recién registrado ya tiene el catálogo inicial (12 categorías)
		this.mvc.perform(como(get("/categorias"))).andExpect(jsonPath("$.length()").value(12));

		this.mvc.perform(como(put("/internal/respaldos/v1").param("simular", "true")).content(this.respaldo()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categorias").value(1))
				.andExpect(jsonPath("$.clasificadas").value(1));
		this.mvc.perform(como(get("/categorias"))).andExpect(jsonPath("$.length()").value(12));

		for (int vez = 0; vez < 2; vez++) {
			this.mvc.perform(como(put("/internal/respaldos/v1")).content(this.respaldo())).andExpect(status().isOk());
		}

		this.mvc.perform(como(get("/categorias")))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(this.ocio))
				.andExpect(jsonPath("$[0].prioridad").value(10));
		this.mvc.perform(como(get("/etiquetas")))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].categoria.id").value(this.ocio));
		this.mvc.perform(como(get("/transacciones/" + this.transaccion)))
				.andExpect(jsonPath("$.categoria.nombre").value("Ocio"))
				.andExpect(jsonPath("$.categoria.origen").value("MIGRACION"))
				.andExpect(jsonPath("$.etiquetas[0].origen").value("MIGRACION"));

		List<String> tipos = this.jdbc.sql("SELECT tipo FROM outbox WHERE usuario_id = ? ORDER BY id")
				.param(UUID.fromString(this.usuario)).query(String.class).list();
		assertThat(tipos).endsWith("ClasificacionRestaurada", "CategoriaGuardada", "EtiquetaGuardada", "TransaccionClasificada");
	}

	private String respaldo() {
		return """
				{"categorias":[{"id":"%s","nombre":"Ocio","color":"#123456","descripcion":null,"ocurrencias":["cine"]}],
				 "etiquetas":[{"id":"%s","nombre":"Cine","descripcion":null,"ocurrencias":["yelmo"],"categoriaId":"%s"}],
				 "transacciones":[
				   {"id":"%s","documentoId":null,"concepto":"CINE YELMO","categoriaId":"%s","etiquetaIds":["%s"]},
				   {"id":"%s","documentoId":null,"concepto":"SIN CLASIFICAR","categoriaId":null,"etiquetaIds":[]}]}"""
				.formatted(this.ocio, this.cine, this.ocio, this.transaccion, this.ocio, this.cine, UUID.randomUUID());
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion) {
		return peticion.with(jwt().jwt(j -> j.subject(this.usuario))).contentType(MediaType.APPLICATION_JSON);
	}
}
