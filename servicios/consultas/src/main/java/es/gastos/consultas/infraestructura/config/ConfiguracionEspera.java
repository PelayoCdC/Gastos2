package es.gastos.consultas.infraestructura.config;

import es.gastos.consultas.aplicacion.consistencia.Pausa;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La pausa real que usa la espera de {@code Gastos-Esperar}. */
@Configuration(proxyBeanMethods = false)
class ConfiguracionEspera {

	/** Con hilos virtuales, dormir el hilo de una petición no bloquea ningún hilo de plataforma. */
	@Bean
	Pausa pausa() {
		return duracion -> {
			try {
				Thread.sleep(duracion);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
	}
}
