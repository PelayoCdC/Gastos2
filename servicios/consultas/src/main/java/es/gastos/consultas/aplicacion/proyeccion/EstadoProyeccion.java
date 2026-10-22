package es.gastos.consultas.aplicacion.proyeccion;

import java.util.OptionalInt;

/** Puerto de salida: qué versión de la proyección hay en las tablas, y vaciarlas para reconstruirla. */
public interface EstadoProyeccion {

	/** Vacío si nunca se ha registrado una versión. */
	OptionalInt version();

	/** Borra todo lo proyectado, incluidas las posiciones de lectura. */
	void vaciar();

	void fijarVersion(int version);
}
