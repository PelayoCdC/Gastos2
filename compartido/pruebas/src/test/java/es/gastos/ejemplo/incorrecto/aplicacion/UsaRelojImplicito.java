package es.gastos.ejemplo.incorrecto.aplicacion;

import java.time.LocalDate;

/** Fixture: caso de uso que lee la fecha del sistema en vez de un reloj inyectado. */
public class UsaRelojImplicito {

	public LocalDate hoy() {
		return LocalDate.now();
	}
}
