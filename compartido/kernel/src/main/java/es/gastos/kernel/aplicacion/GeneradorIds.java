package es.gastos.kernel.aplicacion;

import java.util.UUID;

/**
 * Puerto de salida para generar identidades de agregados nuevos.
 *
 * <p>Los ids se asignan en la aplicación (no en la base de datos) para que el agregado tenga
 * identidad desde que nace y sus eventos puedan referenciarlo antes de guardarse. La
 * implementación por defecto usa UUIDv7 ({@link es.gastos.kernel.ids.Uuid7}), ordenable por tiempo, lo que mantiene
 * compactos los índices primarios; los tests usan un generador determinista.
 */
@FunctionalInterface
public interface GeneradorIds {

	UUID nuevo();
}
