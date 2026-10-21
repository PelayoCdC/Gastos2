package es.gastos.identidad.infraestructura.seguridad;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import es.gastos.compartido.web.PropiedadesSeguridad;
import es.gastos.identidad.aplicacion.EmisorTokens;
import es.gastos.identidad.dominio.usuario.Usuario;
import es.gastos.kernel.ids.Uuid7;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Adaptador de {@link EmisorTokens}: JWT RS256 con los claims que esperan el gateway y los servicios. */
@Component
class EmisorTokensJwt implements EmisorTokens {

	private final JwtEncoder codificador;

	private final ClaveFirma clave;

	private final PropiedadesSeguridad seguridad;

	private final PropiedadesIdentidad identidad;

	private final Clock reloj;

	EmisorTokensJwt(ClaveFirma clave, PropiedadesSeguridad seguridad, PropiedadesIdentidad identidad, Clock reloj) {
		this.codificador = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(clave.clave())));
		this.clave = clave;
		this.seguridad = seguridad;
		this.identidad = identidad;
		this.reloj = reloj;
	}

	@Override
	public TokenEmitido emitir(Usuario usuario) {
		Instant ahora = this.reloj.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(this.seguridad.emisor())
				.audience(List.of(this.seguridad.audiencia()))
				.subject(usuario.id().toString())
				.claim("email", usuario.email().valor())
				.claim("nombre", usuario.nombre().valor())
				.issuedAt(ahora)
				.expiresAt(ahora.plus(this.identidad.expiracion()))
				.id(Uuid7.generar().toString())
				.build();
		JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).keyId(this.clave.clave().getKeyID()).build();

		String token = this.codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
		return new TokenEmitido(token, this.identidad.expiracion().toSeconds());
	}
}
