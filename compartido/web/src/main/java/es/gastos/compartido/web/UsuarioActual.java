package es.gastos.compartido.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inyecta en un parámetro de controlador el {@link es.gastos.kernel.dominio.UsuarioId} del usuario
 * autenticado, sacado del {@code sub} del JWT. Es la <b>única</b> fuente válida del usuario: nunca
 * se acepta del cuerpo ni de los parámetros de la petición.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface UsuarioActual {
}
