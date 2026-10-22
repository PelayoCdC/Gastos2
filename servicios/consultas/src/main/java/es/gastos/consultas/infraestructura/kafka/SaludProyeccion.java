package es.gastos.consultas.infraestructura.kafka;

import es.gastos.compartido.mensajeria.PropiedadesMensajeria;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@code /actuator/health} → {@code proyeccion}: versión, grupo y <b>retraso</b> (eventos publicados que
 * consultas aún no ha proyectado). Sirve para saber cuándo ha terminado una reconstrucción. Si Kafka no
 * responde, el estado es UNKNOWN y no tumba la salud del servicio: las consultas siguen funcionando
 * con lo ya proyectado.
 */
@Component("proyeccion")
class SaludProyeccion implements HealthIndicator, DisposableBean {

	private static final long ESPERA_SEGUNDOS = 3;

	private final KafkaAdmin kafka;

	private final PreparacionProyeccion preparacion;

	private final PropiedadesMensajeria propiedades;

	private AdminClient cliente;

	SaludProyeccion(KafkaAdmin kafka, PreparacionProyeccion preparacion, PropiedadesMensajeria propiedades) {
		this.kafka = kafka;
		this.preparacion = preparacion;
		this.propiedades = propiedades;
	}

	@Override
	public Health health() {
		String grupo = "consultas-v" + this.preparacion.version();
		try {
			return Health.up()
					.withDetail("version", this.preparacion.version())
					.withDetail("grupo", grupo)
					.withDetail("retraso", this.retraso(grupo))
					.build();
		} catch (Exception e) {
			return Health.unknown().withDetail("grupo", grupo).withException(e).build();
		}
	}

	private long retraso(String grupo) throws Exception {
		AdminClient admin = this.cliente();
		Map<TopicPartition, OffsetAndMetadata> confirmadas = admin.listConsumerGroupOffsets(grupo)
				.partitionsToOffsetAndMetadata().get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
		Map<String, TopicDescription> topics = admin.describeTopics(this.propiedades.consume())
				.allTopicNames().get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);

		Map<TopicPartition, OffsetSpec> finales = new HashMap<>();
		topics.forEach((topic, descripcion) -> descripcion.partitions()
				.forEach(particion -> finales.put(new TopicPartition(topic, particion.partition()), OffsetSpec.latest())));
		Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ultimos = admin.listOffsets(finales).all()
				.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);

		long retraso = 0;
		for (var ultimo : ultimos.entrySet()) {
			OffsetAndMetadata confirmada = confirmadas.get(ultimo.getKey());
			retraso += ultimo.getValue().offset() - (confirmada == null ? 0 : confirmada.offset());
		}
		return retraso;
	}

	private synchronized AdminClient cliente() {
		if (this.cliente == null) {
			this.cliente = AdminClient.create(this.kafka.getConfigurationProperties());
		}
		return this.cliente;
	}

	@Override
	public synchronized void destroy() {
		if (this.cliente != null) {
			this.cliente.close();
		}
	}
}
