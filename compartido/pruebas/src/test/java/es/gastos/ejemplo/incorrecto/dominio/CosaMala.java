package es.gastos.ejemplo.incorrecto.dominio;

import es.gastos.ejemplo.incorrecto.infraestructura.Adaptador;
import org.springframework.util.StringUtils;

/** Fixture: dominio que viola la regla de dependencia y usa un framework. */
public class CosaMala {

	public String nombre(Adaptador adaptador) {
		return StringUtils.capitalize(adaptador.leer());
	}
}
