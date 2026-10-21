package es.gastos.kernel.aplicacion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca una clase de la capa de aplicación como caso de uso (puerto de entrada).
 *
 * <p>Es una anotación propia y no {@code @Service} de Spring para que la capa de aplicación no
 * dependa del framework (Clean Architecture, ver {@code docs/adr/0006}). La configuración de cada
 * servicio registra estas clases como beans con un filtro de escaneo.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CasoDeUso {
}
