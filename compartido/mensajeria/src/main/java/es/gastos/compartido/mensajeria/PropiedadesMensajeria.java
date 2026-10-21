package es.gastos.compartido.mensajeria;

import es.gastos.contratos.Topics;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración de mensajería de un servicio ({@code gastos.mensajeria.*}).
 *
 * @param contexto              nombre del servicio (productor de {@code <contexto>.eventos})
 * @param publica               si el servicio publica eventos (consultas no publica)
 * @param particiones           particiones del topic propio
 * @param intervaloRelay        cada cuánto revisa el relay el outbox aunque nadie lo despierte
 * @param loteRelay             eventos máximos por envío
 * @param retencionPublicados   cuánto se conservan en el outbox los eventos ya publicados
 */
@ConfigurationProperties("gastos.mensajeria")
public record PropiedadesMensajeria(
		String contexto,
		Boolean publica,
		Integer particiones,
		Duration intervaloRelay,
		Integer loteRelay,
		Duration retencionPublicados) {

	public PropiedadesMensajeria {
		publica = publica == null || publica;
		particiones = particiones == null ? 3 : particiones;
		intervaloRelay = intervaloRelay == null ? Duration.ofMillis(200) : intervaloRelay;
		loteRelay = loteRelay == null ? 500 : loteRelay;
		retencionPublicados = retencionPublicados == null ? Duration.ofDays(7) : retencionPublicados;
	}

	public String topicPropio() {
		return this.contexto + ".eventos";
	}

	public String topicDlt() {
		return Topics.dlt(this.contexto);
	}
}
