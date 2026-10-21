package es.gastos.ejemplo.correcto.infraestructura;

import es.gastos.ejemplo.correcto.aplicacion.CrearCosa;
import es.gastos.ejemplo.correcto.dominio.Cosa;
import org.springframework.util.StringUtils;

/** Fixture: adaptador de entrada que usa el caso de uso (y puede usar frameworks). */
public class CosaController {

	private final CrearCosa crearCosa;

	public CosaController(CrearCosa crearCosa) {
		this.crearCosa = crearCosa;
	}

	public Cosa crear(String nombre) {
		return this.crearCosa.ejecutar(StringUtils.trimWhitespace(nombre));
	}
}
