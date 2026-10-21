package es.gastos.identidad.infraestructura.seguridad;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Clave RSA con la que identidad firma los tokens. Solo existe aquí: el resto de servicios usan la
 * parte pública que se publica en el JWKS (docs/adr/0008). El {@code kid} es la huella RFC 7638 de
 * la clave, de modo que es estable mientras la clave no cambie.
 */
public final class ClaveFirma {

	private static final Logger LOG = LoggerFactory.getLogger(ClaveFirma.class);

	private final RSAKey clave;

	private ClaveFirma(RSAKey clave) {
		this.clave = clave;
	}

	public static ClaveFirma cargar(PropiedadesIdentidad propiedades) {
		if (propiedades.clavePrivada() != null && propiedades.clavePrivada().exists()) {
			try (InputStream pem = propiedades.clavePrivada().getInputStream()) {
				return new ClaveFirma(desdePem(new String(pem.readAllBytes(), StandardCharsets.US_ASCII)));
			} catch (IOException | GeneralSecurityException | JOSEException e) {
				throw new IllegalStateException("No se pudo leer la clave privada " + propiedades.clavePrivada(), e);
			}
		}

		if (!propiedades.permitirClaveEfimera()) {
			throw new IllegalStateException("Falta la clave privada (gastos.identidad.clave-privada); "
					+ "genera una con scripts/generar-claves-dev.sh");
		}

		LOG.warn("Sin clave privada configurada: se usa una clave efimera; los tokens dejaran de valer al reiniciar");
		try {
			return new ClaveFirma(new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
					.keyIDFromThumbprint(true).generate());
		} catch (JOSEException e) {
			throw new IllegalStateException("No se pudo generar la clave efimera", e);
		}
	}

	static RSAKey desdePem(String pem) throws GeneralSecurityException, JOSEException {
		String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
		KeyFactory fabrica = KeyFactory.getInstance("RSA");
		RSAPrivateCrtKey privada = (RSAPrivateCrtKey) fabrica.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
		RSAPublicKey publica = (RSAPublicKey) fabrica.generatePublic(new RSAPublicKeySpec(privada.getModulus(), privada.getPublicExponent()));

		RSAKey sinKid = new RSAKey.Builder(publica).privateKey(privada).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
		return new RSAKey.Builder(sinKid).keyID(sinKid.computeThumbprint().toString()).build();
	}

	public RSAKey clave() {
		return this.clave;
	}

	public RSAPublicKey clavePublica() {
		try {
			return this.clave.toRSAPublicKey();
		} catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
	}
}
