package es.gastos.identidad;

import com.jayway.jsonpath.JsonPath;
import es.gastos.compartido.mensajeria.LectorSobres;
import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import es.gastos.contratos.Topics;
import es.gastos.contratos.identidad.v1.UsuarioRegistrado;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API de identidad de punta a punta: HTTP → caso de uso → MariaDB → outbox → Kafka. */
@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers({ContenedoresMariaDb.class, ContenedoresKafka.class})
class IdentidadApiIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LectorSobres lector;

	@Test
	void registroSesionYPerfil() throws Exception {
		String email = "ana-" + UUID.randomUUID() + "@ejemplo.es";

		MvcResult registro = this.mvc.perform(post("/registros").contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("nombre", "Ana", "email", email.toUpperCase(), "contrasena", "secreto123"))))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/usuarios/yo"))
				.andExpect(jsonPath("$.email").value(email))
				.andReturn();
		String id = JsonPath.read(registro.getResponse().getContentAsString(), "$.id");

		String token = this.iniciarSesion(email, "secreto123");

		this.mvc.perform(get("/usuarios/yo").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.nombre").value("Ana"));
	}

	@Test
	void erroresComoProblemDetailConCodigo() throws Exception {
		String email = "luis-" + UUID.randomUUID() + "@ejemplo.es";
		String cuerpo = json(Map.of("nombre", "Luis", "email", email, "contrasena", "secreto123"));
		this.mvc.perform(post("/registros").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());

		this.mvc.perform(post("/registros").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.codigo").value("EMAIL_YA_REGISTRADO"));

		this.mvc.perform(post("/registros").contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("nombre", "Luis", "email", "otro-" + email))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores[0].campo").value("contrasena"));

		this.mvc.perform(post("/registros").contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("nombre", "Luis", "email", "otro-" + email, "contrasena", "corta"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CONTRASENA_INVALIDA"));

		this.mvc.perform(post("/sesiones").contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("email", email, "contrasena", "otraclave1"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

		this.mvc.perform(post("/registros").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PETICION_MALFORMADA"));
	}

	@Test
	void sinTokenOConTokenFalsoLasRutasPrivadasDan401() throws Exception {
		this.mvc.perform(get("/usuarios/yo")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/usuarios/yo").header("Authorization", "Bearer no.es.un.jwt")).andExpect(status().isUnauthorized());
	}

	@Test
	void elJwksPublicaSoloLaParteDeLaClave() throws Exception {
		this.mvc.perform(get("/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].alg").value("RS256"))
				.andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
	}

	@Test
	void elRegistroSePublicaEnKafkaConElUsuarioComoClave() throws Exception {
		String email = "eva-" + UUID.randomUUID() + "@ejemplo.es";
		MvcResult registro = this.mvc.perform(post("/registros").contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("nombre", "Eva", "email", email, "contrasena", "secreto123"))))
				.andExpect(status().isCreated())
				.andReturn();
		String id = JsonPath.read(registro.getResponse().getContentAsString(), "$.id");

		try (KafkaConsumer<String, String> consumidor = new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ContenedoresKafka.KAFKA.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "identidad-it-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
			consumidor.subscribe(List.of(Topics.IDENTIDAD));

			UsuarioRegistrado recibido = null;
			long limite = System.currentTimeMillis() + 30_000;
			while (recibido == null && System.currentTimeMillis() < limite) {
				for (ConsumerRecord<String, String> mensaje : consumidor.poll(Duration.ofMillis(300))) {
					if (id.equals(mensaje.key())) {
						recibido = (UsuarioRegistrado) this.lector.leer(mensaje.value()).orElseThrow().datos();
					}
				}
			}

			assertThat(recibido).isEqualTo(new UsuarioRegistrado(UUID.fromString(id), email, "Eva"));
		}
	}

	private String iniciarSesion(String email, String contrasena) throws Exception {
		MvcResult sesion = this.mvc.perform(post("/sesiones").contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("email", email, "contrasena", contrasena))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.expiraEnSegundos").value(86400))
				.andReturn();
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	private static String json(Map<String, String> campos) {
		StringBuilder texto = new StringBuilder("{");
		campos.forEach((clave, valor) -> texto.append(texto.length() > 1 ? "," : "")
				.append('"').append(clave).append("\":\"").append(valor).append('"'));
		return texto.append('}').toString();
	}
}
