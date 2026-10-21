package es.gastos.identidad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Servicio de identidad: registro, inicio de sesión y emisión de JWT (docs/dominio/identidad.md). */
@SpringBootApplication
public class IdentidadAplicacion {

	public static void main(String[] args) {
		SpringApplication.run(IdentidadAplicacion.class, args);
	}
}
