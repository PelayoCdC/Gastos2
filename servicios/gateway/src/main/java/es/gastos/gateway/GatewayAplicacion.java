package es.gastos.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entrada única al sistema (docs/adr/0019). Las rutas están en application.yaml. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayAplicacion {

	public static void main(String[] args) {
		SpringApplication.run(GatewayAplicacion.class, args);
	}
}
