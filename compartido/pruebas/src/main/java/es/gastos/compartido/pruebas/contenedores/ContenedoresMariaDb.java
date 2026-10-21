package es.gastos.compartido.pruebas.contenedores;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mariadb.MariaDBContainer;

/**
 * MariaDB real para los tests de integración (misma versión que en {@code compose.yaml}).
 *
 * <p>Uso: {@code @ImportTestcontainers(ContenedoresMariaDb.class)} en el test. Al ser un campo
 * estático se arranca una sola vez por JVM y lo comparten todos los contextos de Spring; Boot
 * configura el datasource solo gracias a {@code @ServiceConnection}.
 */
public interface ContenedoresMariaDb {

	String IMAGEN = "mariadb:11.8";

	@ServiceConnection
	MariaDBContainer MARIADB = new MariaDBContainer(IMAGEN)
			.withDatabaseName("pruebas")
			.withUrlParam("serverTimezone", "UTC");
}
