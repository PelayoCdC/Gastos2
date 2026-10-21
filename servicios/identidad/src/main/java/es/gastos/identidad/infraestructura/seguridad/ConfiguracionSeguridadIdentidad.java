package es.gastos.identidad.infraestructura.seguridad;

import es.gastos.compartido.web.PropiedadesSeguridad;
import es.gastos.compartido.web.SeguridadAutoConfiguracion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Identidad valida sus propios tokens (para {@code /usuarios/yo}) con su clave pública en memoria,
 * en vez de pedirse el JWKS a sí misma por HTTP. Los validadores son los mismos que en el resto.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesIdentidad.class)
class ConfiguracionSeguridadIdentidad {

	@Bean
	ClaveFirma claveFirma(PropiedadesIdentidad propiedades) {
		return ClaveFirma.cargar(propiedades);
	}

	@Bean
	JwtDecoder decodificadorJwt(ClaveFirma clave, PropiedadesSeguridad propiedades) {
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withPublicKey(clave.clavePublica())
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		decodificador.setJwtValidator(SeguridadAutoConfiguracion.validadores(propiedades));
		return decodificador;
	}
}
