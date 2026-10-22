package es.gastos.consultas.infraestructura.kafka;

import es.gastos.consultas.aplicacion.proyeccion.ReconstruirProyeccion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prepara la proyección al arrancar, después de las migraciones y antes de que los consumidores de
 * Kafka empiecen (los contenedores arrancan cuando ya están creados todos los beans). Si la versión
 * configurada cambia, las tablas se vacían y el consumidor relee los topics desde el principio.
 *
 * <p>Releer no se deja solo en manos del grupo nuevo ({@code consultas-v<version>}, que empieza por
 * {@code earliest}): si alguien vuelve a una versión anterior, su grupo ya tiene posiciones guardadas al
 * final del topic y no releería nada. Por eso, además, el consumidor se reposiciona al principio
 * (ver {@link ConsumidorEventos}).
 */
@Component
@DependsOnDatabaseInitialization
public class PreparacionProyeccion implements InitializingBean {

	private static final Logger LOG = LoggerFactory.getLogger(PreparacionProyeccion.class);

	private final ReconstruirProyeccion reconstruir;

	private final int version;

	private final AtomicBoolean releerDesdeElPrincipio = new AtomicBoolean();

	PreparacionProyeccion(ReconstruirProyeccion reconstruir, @Value("${gastos.consultas.proyeccion.version:1}") int version) {
		this.reconstruir = reconstruir;
		this.version = version;
	}

	@Override
	public void afterPropertiesSet() {
		this.preparar(this.version);
	}

	/** También lo usan los tests para reconstruir sin reiniciar (con el consumidor parado). */
	public void preparar(int versionPedida) {
		if (this.reconstruir.prepararVersion(versionPedida)) {
			LOG.warn("Proyeccion de consultas en version {}: tablas vaciadas, se releen los topics desde el principio", versionPedida);
			this.releerDesdeElPrincipio.set(true);
		} else {
			LOG.info("Proyeccion de consultas en version {}: se continua donde se quedo", versionPedida);
		}
	}

	public int version() {
		return this.version;
	}

	/** Devuelve si hay que releer desde el principio, y solo una vez. */
	boolean consumirRelectura() {
		return this.releerDesdeElPrincipio.getAndSet(false);
	}
}
