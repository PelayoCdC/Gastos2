package es.gastos.clasificacion.infraestructura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/** Activa {@code @Retryable} (Spring Framework 7) en las llamadas HTTP a otros servicios. */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
class ConfiguracionResiliencia {
}
