package es.gastos.compartido.mensajeria;

import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configura la mensajería común de un servicio a partir de {@code gastos.mensajeria.*}:
 * unidad de trabajo, outbox + relay (si el servicio publica y declara su {@link TraductorEventos}),
 * inbox, lector de sobres, topics propios con retención infinita, DLT y migraciones compartidas.
 */
@AutoConfiguration(after = {DataSourceTransactionManagerAutoConfiguration.class, JdbcClientAutoConfiguration.class,
		KafkaAutoConfiguration.class})
@EnableConfigurationProperties(PropiedadesMensajeria.class)
public class MensajeriaAutoConfiguracion {

	/** Las migraciones de outbox/inbox (V0_x) se aplican antes que las propias del servicio (V1+). */
	@Bean
	FlywayConfigurationCustomizer migracionesCompartidas() {
		return configuracion -> {
			List<String> ubicaciones = new ArrayList<>(Arrays.stream(configuracion.getLocations())
					.map(org.flywaydb.core.api.Location::getDescriptor)
					.toList());
			ubicaciones.addFirst("classpath:db/compartido");
			configuracion.locations(ubicaciones.toArray(String[]::new));
		};
	}

	/** Reloj por defecto (lo declara también compartido/web; el primero que se procesa gana). */
	@Bean
	@ConditionalOnMissingBean
	Clock reloj() {
		return Clock.system(java.time.ZoneId.of("Europe/Madrid"));
	}

	@Bean
	@ConditionalOnMissingBean
	UnidadDeTrabajo unidadDeTrabajo(PlatformTransactionManager gestor) {
		return new UnidadDeTrabajoTransaccional(gestor);
	}

	@Bean
	OutboxRepositorio outboxRepositorio(JdbcClient jdbc) {
		return new OutboxRepositorio(jdbc);
	}

	@Bean
	Inbox inbox(JdbcClient jdbc, Clock reloj) {
		return new Inbox(jdbc, reloj);
	}

	@Bean
	LectorSobres lectorSobres(JsonMapper json) {
		return new LectorSobres(json);
	}

	@Bean
	@ConditionalOnProperty(name = "gastos.mensajeria.publica", havingValue = "true", matchIfMissing = true)
	RelayOutbox relayOutbox(OutboxRepositorio outbox, KafkaTemplate<String, String> kafka, PlatformTransactionManager gestor,
			JsonMapper json, Clock reloj, PropiedadesMensajeria propiedades) {
		return new RelayOutbox(outbox, kafka, new TransactionTemplate(gestor), json, reloj, propiedades);
	}

	@Bean
	@ConditionalOnBean(TraductorEventos.class)
	@ConditionalOnMissingBean(PublicadorEventos.class)
	PublicadorEventos publicadorEventos(TraductorEventos traductor, OutboxRepositorio outbox, JsonMapper json, Clock reloj,
			ObjectProvider<RelayOutbox> relay, PropiedadesMensajeria propiedades) {
		return new PublicadorEventosOutbox(traductor, outbox, json, reloj,
				() -> relay.ifAvailable(RelayOutbox::despertar), propiedades.contexto());
	}

	/** Cabecera {@code Gastos-Secuencia} en las respuestas de los comandos (docs/adr/0011). */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	@ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
	static class SecuenciaEnRespuestas {

		@Bean
		FilterRegistrationBean<FiltroSecuenciaPublicada> filtroSecuenciaPublicada() {
			FilterRegistrationBean<FiltroSecuenciaPublicada> registro = new FilterRegistrationBean<>(new FiltroSecuenciaPublicada());
			// Por fuera de la cadena de seguridad, para envolver la respuesta antes que nadie
			registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
			return registro;
		}
	}

	/** Topic propio con retención infinita: es el log del que se reconstruyen las proyecciones (docs/adr/0010). */
	@Bean
	@ConditionalOnProperty(name = "gastos.mensajeria.publica", havingValue = "true", matchIfMissing = true)
	NewTopic topicPropio(PropiedadesMensajeria propiedades) {
		return topicDeEventos(propiedades.topicPropio(), propiedades.particiones());
	}

	@Bean
	NewTopic topicDlt(PropiedadesMensajeria propiedades) {
		return TopicBuilder.name(propiedades.topicDlt()).partitions(1).replicas(1).build();
	}

	@Bean
	KafkaAdmin.NewTopics topicsConsumidos(PropiedadesMensajeria propiedades) {
		return new KafkaAdmin.NewTopics(propiedades.consume().stream()
				.map(topic -> topicDeEventos(topic, propiedades.particiones()))
				.toArray(NewTopic[]::new));
	}

	private static NewTopic topicDeEventos(String nombre, int particiones) {
		return TopicBuilder.name(nombre)
				.partitions(particiones)
				.replicas(1)
				.config(TopicConfig.RETENTION_MS_CONFIG, "-1")
				.config(TopicConfig.RETENTION_BYTES_CONFIG, "-1")
				.build();
	}

	/**
	 * Un mensaje que falla tres veces seguidas va al DLT del servicio y la partición sigue avanzando;
	 * las cabeceras que añade Spring indican el topic, la partición y el error originales.
	 */
	@Bean
	@ConditionalOnMissingBean(CommonErrorHandler.class)
	CommonErrorHandler manejadorErroresKafka(KafkaTemplate<Object, Object> kafka, PropiedadesMensajeria propiedades) {
		DeadLetterPublishingRecoverer alDlt = new DeadLetterPublishingRecoverer(kafka,
				(registro, error) -> new TopicPartition(propiedades.topicDlt(), -1));
		return new DefaultErrorHandler(alDlt, new FixedBackOff(1000L, 2L));
	}
}
