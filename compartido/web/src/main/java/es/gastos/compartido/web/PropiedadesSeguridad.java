package es.gastos.compartido.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuración del resource server común ({@code gastos.seguridad.*}).
 *
 * @param jwkSetUri     dónde publica identidad sus claves públicas (JWKS)
 * @param emisor        valor exigido en el claim {@code iss}
 * @param audiencia     valor que debe contener el claim {@code aud}
 * @param rutasPublicas rutas sin token, opcionalmente con método: {@code "POST /registros"}
 */
@ConfigurationProperties("gastos.seguridad")
public record PropiedadesSeguridad(String jwkSetUri, String emisor, String audiencia, List<String> rutasPublicas) {

	public PropiedadesSeguridad {
		emisor = emisor == null ? "gastos-identidad" : emisor;
		audiencia = audiencia == null ? "gastos-api" : audiencia;
		rutasPublicas = rutasPublicas == null ? List.of() : List.copyOf(rutasPublicas);
	}
}
