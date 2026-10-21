package es.gastos.importacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Servicio de importación: la capa anticorrupción de los formatos de banco (docs/dominio/importacion.md). */
@SpringBootApplication
public class ImportacionAplicacion {

	public static void main(String[] args) {
		SpringApplication.run(ImportacionAplicacion.class, args);
	}
}
