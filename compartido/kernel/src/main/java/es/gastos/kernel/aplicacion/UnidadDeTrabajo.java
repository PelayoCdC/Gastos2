package es.gastos.kernel.aplicacion;

import java.util.function.Supplier;

/**
 * Puerto de salida que delimita una transacción: todo lo que se ejecuta dentro se confirma o se
 * deshace junto (cambios de agregados y escritura de eventos en el outbox).
 *
 * <p>Sustituye a {@code @Transactional}: la frontera transaccional queda visible en el propio caso
 * de uso y la capa de aplicación no depende de Spring. En los tests unitarios se usa una
 * implementación que simplemente ejecuta el bloque. Ver {@code docs/adr/0006}.
 */
public interface UnidadDeTrabajo {

	<T> T ejecutar(Supplier<T> trabajo);

	default void ejecutar(Runnable trabajo) {
		this.ejecutar(() -> {
			trabajo.run();
			return null;
		});
	}

	/** Transacción de solo lectura (lecturas consistentes, sin escrituras). */
	<T> T ejecutarLectura(Supplier<T> trabajo);
}
