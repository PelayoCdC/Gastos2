package es.gastos.compartido.pruebas.spikes;

import es.gastos.compartido.pruebas.contenedores.ContenedoresKafka;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike de F0: Testcontainers 2 arranca Kafka (KRaft) sobre Docker 29 y los mensajes con la misma
 * clave llegan en orden (la base de la decisión de usar el usuario como clave, docs/adr/0004).
 */
class SpikeKafkaIT {

	@BeforeAll
	static void arrancar() {
		ContenedoresKafka.KAFKA.start();
	}

	@Test
	void losMensajesConLaMismaClaveLleganEnOrden() throws Exception {
		String servidores = ContenedoresKafka.KAFKA.getBootstrapServers();
		String topic = "spike.eventos";

		try (KafkaProducer<String, String> productor = new KafkaProducer<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servidores,
				ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
				ProducerConfig.ACKS_CONFIG, "all"), new StringSerializer(), new StringSerializer())) {
			for (int i = 1; i <= 5; i++) {
				productor.send(new ProducerRecord<>(topic, "usuario-1", "evento-" + i)).get();
			}
		}

		List<String> recibidos = new ArrayList<>();
		try (KafkaConsumer<String, String> consumidor = new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servidores,
				ConsumerConfig.GROUP_ID_CONFIG, "spike",
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
			consumidor.subscribe(List.of(topic));
			long limite = System.currentTimeMillis() + 30_000;
			while (recibidos.size() < 5 && System.currentTimeMillis() < limite) {
				for (ConsumerRecord<String, String> registro : consumidor.poll(Duration.ofMillis(500))) {
					recibidos.add(registro.value());
				}
			}
		}

		assertThat(recibidos).containsExactly("evento-1", "evento-2", "evento-3", "evento-4", "evento-5");
	}
}
