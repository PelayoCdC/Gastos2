package es.gastos.identidad.infraestructura.config;

import es.gastos.kernel.aplicacion.CasoDeUso;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Registra como beans las clases {@code @CasoDeUso} de la capa de aplicación. Así la aplicación no
 * necesita anotaciones de Spring (docs/adr/0006): el framework entra por aquí, desde fuera.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "es.gastos.identidad.aplicacion", useDefaultFilters = false,
		includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = CasoDeUso.class))
class ConfiguracionCasosDeUso {
}
