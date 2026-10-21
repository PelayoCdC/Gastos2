package es.gastos.compartido.pruebas.dobles;

import es.gastos.kernel.aplicacion.UnidadDeTrabajo;

import java.util.function.Supplier;

/** Unidad de trabajo para tests unitarios: ejecuta el bloque sin transacción y cuenta las ejecuciones. */
public class UnidadDeTrabajoDirecta implements UnidadDeTrabajo {

	private int ejecuciones;

	@Override
	public <T> T ejecutar(Supplier<T> trabajo) {
		this.ejecuciones++;
		return trabajo.get();
	}

	@Override
	public <T> T ejecutarLectura(Supplier<T> trabajo) {
		return trabajo.get();
	}

	public int ejecuciones() {
		return this.ejecuciones;
	}
}
