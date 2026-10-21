package es.gastos.importacion.infraestructura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/** Activa {@code @Retryable} (Spring Framework 7) en la llamada a movimientos. */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
class ConfiguracionResiliencia {
}
