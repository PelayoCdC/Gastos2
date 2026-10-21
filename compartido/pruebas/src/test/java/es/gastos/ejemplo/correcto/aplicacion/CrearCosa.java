package es.gastos.ejemplo.correcto.aplicacion;

import es.gastos.ejemplo.correcto.dominio.Cosa;
import es.gastos.kernel.aplicacion.CasoDeUso;

import java.time.Clock;
import java.time.LocalDate;

/** Fixture: caso de uso que depende solo del dominio y usa un reloj inyectado. */
@CasoDeUso
public class CrearCosa {

	private final Clock reloj;

	public CrearCosa(Clock reloj) {
		this.reloj = reloj;
	}

	public Cosa ejecutar(String nombre) {
		return new Cosa(nombre + " " + LocalDate.now(this.reloj));
	}
}
