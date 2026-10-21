package es.gastos.importacion.infraestructura.config;

import es.gastos.kernel.aplicacion.CasoDeUso;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/** Registra las clases {@code @CasoDeUso} de la aplicación como beans (docs/adr/0006). */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "es.gastos.importacion.aplicacion", useDefaultFilters = false,
		includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = CasoDeUso.class))
class ConfiguracionCasosDeUso {
}
