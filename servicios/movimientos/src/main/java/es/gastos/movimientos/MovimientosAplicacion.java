package es.gastos.movimientos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Servicio de movimientos: hechos bancarios (docs/dominio/movimientos.md). */
@SpringBootApplication
public class MovimientosAplicacion {

	public static void main(String[] args) {
		SpringApplication.run(MovimientosAplicacion.class, args);
	}
}
