package es.gastos.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuración de seguridad y CORS del gateway ({@code gastos.gateway.*}).
 *
 * @param jwkSetUri          JWKS de identidad (se pide directamente al servicio, no a través del gateway)
 * @param emisor             claim {@code iss} exigido
 * @param audiencia          valor exigido en {@code aud}
 * @param origenesPermitidos orígenes del navegador autorizados por CORS
 */
@ConfigurationProperties("gastos.gateway")
public record PropiedadesGateway(String jwkSetUri, String emisor, String audiencia, List<String> origenesPermitidos) {

	public PropiedadesGateway {
		emisor = emisor == null ? "gastos-identidad" : emisor;
		audiencia = audiencia == null ? "gastos-api" : audiencia;
		origenesPermitidos = origenesPermitidos == null ? List.of("http://localhost:4200") : List.copyOf(origenesPermitidos);
	}
}
