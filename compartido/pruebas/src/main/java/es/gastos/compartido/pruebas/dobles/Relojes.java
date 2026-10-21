package es.gastos.compartido.pruebas.dobles;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Relojes fijos para tests: el sistema trabaja en la zona {@code Europe/Madrid}. */
public final class Relojes {

	public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

	private Relojes() {
	}

	/** Reloj parado a las 12:00 del día indicado ({@code yyyy-MM-dd}). */
	public static Clock fijo(String fecha) {
		return Clock.fixed(LocalDate.parse(fecha).atTime(12, 0).atZone(ZONA).toInstant(), ZONA);
	}
}
