package es.gastos.consultas.aplicacion.proyeccion;

import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;

import java.util.OptionalInt;

/**
 * Reconstrucción de consultas reproduciendo los topics (docs/adr/0010). Al arrancar, antes de
 * consumir, se compara la versión configurada de la proyección con la que tienen las tablas:
 * <ul>
 *   <li>Si es la misma, no se hace nada y el consumidor sigue donde lo dejó.</li>
 *   <li>Si es distinta (o es la primera vez y ya había datos de una versión sin registrar), se vacían
 *   las tablas y el consumidor tiene que releer los topics desde el principio.</li>
 * </ul>
 * Así cambiar el esquema de lectura, o reparar una proyección estropeada, es subir un número.
 */
@CasoDeUso
public class ReconstruirProyeccion {

	private final EstadoProyeccion estado;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public ReconstruirProyeccion(EstadoProyeccion estado, UnidadDeTrabajo unidadDeTrabajo) {
		this.estado = estado;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	/** Devuelve si hay que releer los topics desde el principio. */
	public boolean prepararVersion(int version) {
		return this.unidadDeTrabajo.ejecutar(() -> {
			OptionalInt actual = this.estado.version();
			if (actual.isPresent() && actual.getAsInt() == version) {
				return false;
			}
			this.estado.vaciar();
			this.estado.fijarVersion(version);
			return true;
		});
	}
}
