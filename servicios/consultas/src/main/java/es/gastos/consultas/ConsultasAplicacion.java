package es.gastos.consultas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Servicio de consultas: el lado de lectura CQRS (docs/dominio/consultas.md). */
@SpringBootApplication
public class ConsultasAplicacion {

	public static void main(String[] args) {
		SpringApplication.run(ConsultasAplicacion.class, args);
	}
}
