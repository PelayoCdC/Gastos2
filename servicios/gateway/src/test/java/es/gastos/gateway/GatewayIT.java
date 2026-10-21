package es.gastos.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El gateway contra un servicio simulado (un HttpServer del JDK que también publica el JWKS):
 * enrutado con reescritura, token obligatorio, rutas internas bloqueadas, CORS y límite de subida.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIT {

	record PeticionRecibida(String metodo, String ruta, String autorizacion) {
	}

	private static final RSAKey CLAVE = generarClave();

	private static final List<PeticionRecibida> RECIBIDAS = new CopyOnWriteArrayList<>();

	private static final HttpServer SERVICIO = arrancarServicioSimulado();

	@LocalServerPort
	private int puerto;

	private WebTestClient cliente;

	@DynamicPropertySource
	static void configurar(DynamicPropertyRegistry registro) {
		String base = "http://localhost:" + SERVICIO.getAddress().getPort();
		for (String servicio : List.of("identidad", "movimientos", "clasificacion", "importacion", "consultas")) {
			registro.add("gastos.servicios." + servicio, () -> base);
		}
		// El JWKS sigue en el simulado; "consultas" apunta a un puerto sin nadie escuchando
		registro.add("gastos.gateway.jwk-set-uri", () -> base + "/.well-known/jwks.json");
		registro.add("gastos.servicios.consultas", () -> "http://localhost:1");
	}

	@AfterAll
	static void parar() {
		SERVICIO.stop(0);
	}

	@BeforeEach
	void preparar() {
		RECIBIDAS.clear();
		this.cliente = WebTestClient.bindToServer().baseUrl("http://localhost:" + this.puerto).build();
	}

	@Test
	void conTokenValidoEnrutaAlServicioQuitandoElPrefijoYConservaElToken() {
		String token = token("gastos-identidad", "gastos-api");

		this.cliente.get().uri("/api/movimientos/cuentas?pagina=0")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).isEqualTo("respuesta de GET /cuentas?pagina=0");

		assertThat(RECIBIDAS).containsExactly(new PeticionRecibida("GET", "/cuentas?pagina=0", "Bearer " + token));
	}

	@Test
	void sinTokenOConTokenDeOtraAudienciaOEmisorDa401SinLlegarAlServicio() {
		this.cliente.get().uri("/api/consultas/transacciones").exchange().expectStatus().isUnauthorized();
		this.cliente.get().uri("/api/consultas/transacciones")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("gastos-identidad", "otra-api"))
				.exchange().expectStatus().isUnauthorized();
		this.cliente.get().uri("/api/consultas/transacciones")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("otro-emisor", "gastos-api"))
				.exchange().expectStatus().isUnauthorized();

		assertThat(RECIBIDAS).isEmpty();
	}

	@Test
	void elRegistroYElInicioDeSesionSonPublicos() {
		this.cliente.post().uri("/api/identidad/sesiones").bodyValue("{}").exchange().expectStatus().isOk();
		this.cliente.post().uri("/api/identidad/registros").bodyValue("{}").exchange().expectStatus().isOk();

		assertThat(RECIBIDAS).extracting(PeticionRecibida::ruta).containsExactly("/sesiones", "/registros");
	}

	@Test
	void lasRutasInternasNuncaSalenAunqueHayaToken() {
		this.cliente.post().uri("/api/movimientos/internal/extractos")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("gastos-identidad", "gastos-api"))
				.bodyValue("{}")
				.exchange()
				.expectStatus().isForbidden();

		assertThat(RECIBIDAS).isEmpty();
	}

	@Test
	void respondeAlPreflightCorsDelFront() {
		this.cliente.options().uri("/api/movimientos/cuentas")
				.header(HttpHeaders.ORIGIN, "http://localhost:4200")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200");

		this.cliente.options().uri("/api/movimientos/cuentas")
				.header(HttpHeaders.ORIGIN, "http://malicioso.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void unServicioQueNoRespondeDa503ConProblemDetail() {
		this.cliente.get().uri("/api/consultas/informes/bizum")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("gastos-identidad", "gastos-api"))
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
				.expectHeader().contentTypeCompatibleWith("application/problem+json")
				.expectBody().jsonPath("$.codigo").isEqualTo("SERVICIO_NO_DISPONIBLE");
	}

	@Test
	void rechazaSubidasDeMasDeDiezMegasCon413() {
		byte[] fichero = new byte[10 * 1024 * 1024 + 1];

		this.cliente.post().uri("/api/importacion/importaciones")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token("gastos-identidad", "gastos-api"))
				.bodyValue(fichero)
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

		assertThat(RECIBIDAS).isEmpty();
	}

	private static String token(String emisor, String audiencia) {
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(emisor)
				.audience(List.of(audiencia))
				.subject("0192f3a1-7c4e-7a10-8b2c-3d4e5f607182")
				.issuedAt(ahora)
				.expiresAt(ahora.plus(1, ChronoUnit.HOURS))
				.build();
		NimbusJwtEncoder codificador = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(CLAVE)));
		JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).keyId(CLAVE.getKeyID()).build();
		return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
	}

	private static RSAKey generarClave() {
		try {
			return new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).keyIDFromThumbprint(true).generate();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** Simula todos los servicios: publica el JWKS y responde eco a cualquier otra ruta. */
	private static HttpServer arrancarServicioSimulado() {
		try {
			HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			servidor.createContext("/.well-known/jwks.json", intercambio -> {
				byte[] jwks = new JWKSet(CLAVE.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
				intercambio.getResponseHeaders().add("Content-Type", "application/json");
				intercambio.sendResponseHeaders(200, jwks.length);
				intercambio.getResponseBody().write(jwks);
				intercambio.close();
			});
			servidor.createContext("/", intercambio -> {
				String ruta = intercambio.getRequestURI().toString();
				RECIBIDAS.add(new PeticionRecibida(intercambio.getRequestMethod(), ruta,
						intercambio.getRequestHeaders().getFirst("Authorization")));
				intercambio.getRequestBody().readAllBytes();
				byte[] respuesta = ("respuesta de " + intercambio.getRequestMethod() + " " + ruta).getBytes(StandardCharsets.UTF_8);
				intercambio.sendResponseHeaders(200, respuesta.length);
				intercambio.getResponseBody().write(respuesta);
				intercambio.close();
			});
			servidor.start();
			return servidor;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
