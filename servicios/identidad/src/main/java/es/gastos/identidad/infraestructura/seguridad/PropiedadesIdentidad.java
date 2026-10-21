package es.gastos.identidad.infraestructura.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Configuración propia de identidad ({@code gastos.identidad.*}).
 *
 * @param clavePrivada         PEM PKCS#8 con la clave RSA de firma (lo genera scripts/generar-claves-dev.sh)
 * @param permitirClaveEfimera si falta la clave, generar una en memoria (solo desarrollo: los tokens dejan
 *                             de valer al reiniciar)
 * @param expiracion           validez de los tokens emitidos
 */
@ConfigurationProperties("gastos.identidad")
public record PropiedadesIdentidad(Resource clavePrivada, Boolean permitirClaveEfimera, Duration expiracion) {

	public PropiedadesIdentidad {
		permitirClaveEfimera = permitirClaveEfimera != null && permitirClaveEfimera;
		expiracion = expiracion == null ? Duration.ofHours(24) : expiracion;
	}
}
