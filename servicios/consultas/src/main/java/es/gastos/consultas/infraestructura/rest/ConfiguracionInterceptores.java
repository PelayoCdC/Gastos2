package es.gastos.consultas.infraestructura.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** {@code Gastos-Esperar} se atiende en todas las consultas. */
@Configuration(proxyBeanMethods = false)
class ConfiguracionInterceptores implements WebMvcConfigurer {

	private final InterceptorEsperaConsistencia esperaConsistencia;

	ConfiguracionInterceptores(InterceptorEsperaConsistencia esperaConsistencia) {
		this.esperaConsistencia = esperaConsistencia;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registro) {
		registro.addInterceptor(this.esperaConsistencia).addPathPatterns("/transacciones/**", "/informes/**");
	}
}
