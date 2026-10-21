package es.gastos.clasificacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Servicio de clasificación: el core domain (docs/dominio/clasificacion.md). */
@SpringBootApplication
public class ClasificacionAplicacion {

	public static void main(String[] args) {
		SpringApplication.run(ClasificacionAplicacion.class, args);
	}
}
