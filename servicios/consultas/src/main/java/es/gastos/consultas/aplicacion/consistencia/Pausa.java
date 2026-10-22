package es.gastos.consultas.aplicacion.consistencia;

import java.time.Duration;

/** Puerto de salida: esperar un rato. Los tests lo sustituyen para no dormir de verdad. */
@FunctionalInterface
public interface Pausa {

	void pausar(Duration duracion);
}
