package es.gastos.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;

/**
 * Seguridad en el borde: el gateway rechaza lo que no lleve un JWT válido de identidad antes de
 * gastar recursos en los servicios (que vuelven a validarlo: defensa en profundidad), y bloquea
 * siempre las rutas internas {@code /api/{contexto}/internal/**}, pensadas solo para llamadas
 * entre servicios dentro de la red de compose.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SeguridadGateway {

	@Bean
	SecurityWebFilterChain cadenaDeSeguridad(ServerHttpSecurity http) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.cors(Customizer.withDefaults())
				.authorizeExchange(rutas -> rutas
						// Primero lo prohibido: aunque lleve token, lo interno no sale del clúster
						.pathMatchers("/api/*/internal/**").denyAll()
						.pathMatchers(HttpMethod.OPTIONS).permitAll()
						.pathMatchers(HttpMethod.POST, "/api/identidad/registros", "/api/identidad/sesiones").permitAll()
						.pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.anyExchange().authenticated())
				.oauth2ResourceServer(servidor -> servidor.jwt(Customizer.withDefaults()))
				.build();
	}

	@Bean
	ReactiveJwtDecoder decodificadorJwt(PropiedadesGateway propiedades) {
		NimbusReactiveJwtDecoder decodificador = NimbusReactiveJwtDecoder.withJwkSetUri(propiedades.jwkSetUri())
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.build();
		OAuth2TokenValidator<Jwt> audiencia = new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
				aud -> aud != null && aud.contains(propiedades.audiencia()));
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(propiedades.emisor()), audiencia));
		return decodificador;
	}

	/** CORS solo aquí: los servicios no son accesibles desde el navegador. */
	@Bean
	CorsConfigurationSource configuracionCors(PropiedadesGateway propiedades) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(propiedades.origenesPermitidos());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("*"));
		cors.setExposedHeaders(List.of("Location", "Content-Disposition", "Gastos-Secuencia", "Gastos-Desfase"));
		cors.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", cors);
		return fuente;
	}
}
