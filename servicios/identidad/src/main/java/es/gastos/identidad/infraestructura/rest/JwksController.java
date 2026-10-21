package es.gastos.identidad.infraestructura.rest;

import com.nimbusds.jose.jwk.JWKSet;
import es.gastos.identidad.infraestructura.seguridad.ClaveFirma;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publica la parte <b>pública</b> de la clave de firma (JWKS) para que el gateway y los servicios
 * validen los tokens sin compartir ningún secreto (docs/adr/0008).
 */
@RestController
class JwksController {

	private final Map<String, Object> jwks;

	JwksController(ClaveFirma clave) {
		this.jwks = new JWKSet(clave.clave().toPublicJWK()).toJSONObject();
	}

	@GetMapping("/.well-known/jwks.json")
	Map<String, Object> jwks() {
		return this.jwks;
	}
}
