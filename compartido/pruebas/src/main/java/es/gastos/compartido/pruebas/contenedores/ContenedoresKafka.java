package es.gastos.compartido.pruebas.contenedores;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka real (modo KRaft, sin ZooKeeper) para los tests de integración, misma imagen que en
 * {@code compose.yaml}. Uso: {@code @ImportTestcontainers(ContenedoresKafka.class)}.
 */
public interface ContenedoresKafka {

	String IMAGEN = "apache/kafka:4.2.2";

	@ServiceConnection
	KafkaContainer KAFKA = new KafkaContainer(IMAGEN);
}
