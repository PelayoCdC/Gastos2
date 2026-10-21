package es.gastos.compartido.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;

/**
 * Todos los servicios son resource servers OAuth2: aceptan solo JWT firmados por identidad (RS256,
 * validados con su JWKS), con el emisor y la audiencia esperados y sin caducar. Sin sesión ni CSRF
 * (API sin estado con token en cabecera). Ver docs/adr/0008.
 */
@AutoConfiguration
@EnableConfigurationProperties(PropiedadesSeguridad.class)
public class SeguridadAutoConfiguracion {

	@Bean
	@ConditionalOnMissingBean(SecurityFilterChain.class)
	SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, PropiedadesSeguridad propiedades) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(peticiones -> {
					peticiones.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll();
					for (String ruta : propiedades.rutasPublicas()) {
						String[] partes = ruta.trim().split("\\s+", 2);
						if (partes.length == 2) {
							peticiones.requestMatchers(HttpMethod.valueOf(partes[0]), partes[1]).permitAll();
						} else {
							peticiones.requestMatchers(partes[0]).permitAll();
						}
					}
					peticiones.anyRequest().authenticated();
				})
				.oauth2ResourceServer(servidor -> servidor.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	@ConditionalOnMissingBean(JwtDecoder.class)
	JwtDecoder decodificadorJwt(PropiedadesSeguridad propiedades) {
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withJwkSetUri(propiedades.jwkSetUri())
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.build();
		decodificador.setJwtValidator(validadores(propiedades));
		return decodificador;
	}

	/** Firma y caducidad (por defecto) más emisor y audiencia: reutilizable por identidad y el gateway. */
	public static OAuth2TokenValidator<Jwt> validadores(PropiedadesSeguridad propiedades) {
		OAuth2TokenValidator<Jwt> audiencia = new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
				aud -> aud != null && aud.contains(propiedades.audiencia()));
		return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(propiedades.emisor()), audiencia);
	}
}
