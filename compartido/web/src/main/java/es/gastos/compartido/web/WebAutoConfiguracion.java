package es.gastos.compartido.web;

import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.ids.Uuid7;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

/**
 * Piezas comunes de los servicios HTTP: errores RFC 9457, {@code @UsuarioActual} y los adaptadores
 * por defecto de los puertos transversales del kernel ({@link Clock} en la zona de negocio y
 * {@link GeneradorIds} con UUIDv7). Cada servicio puede sustituirlos declarando su propio bean.
 */
@AutoConfiguration
@Import(ManejadorErrores.class)
public class WebAutoConfiguracion {

	/** Zona en la que se interpretan "hoy" y las fechas de negocio. */
	public static final ZoneId ZONA_NEGOCIO = ZoneId.of("Europe/Madrid");

	@Bean
	@ConditionalOnMissingBean
	Clock reloj() {
		return Clock.system(ZONA_NEGOCIO);
	}

	@Bean
	@ConditionalOnMissingBean
	GeneradorIds generadorIds() {
		return new Uuid7();
	}

	@Bean
	WebMvcConfigurer configuracionUsuarioActual() {
		return new WebMvcConfigurer() {
			@Override
			public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolutores) {
				resolutores.add(new ResolutorUsuarioActual());
			}
		};
	}
}
