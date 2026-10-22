package es.gastos.consultas.dominio.informes;

import es.gastos.kernel.dominio.ExcepcionDominio;

import java.time.LocalDate;

/** Intervalo de fechas contables de un informe; los extremos nulos están abiertos y ambos se incluyen. */
public record Periodo(LocalDate desde, LocalDate hasta) {

	public static final Periodo TODO = new Periodo(null, null);

	public Periodo {
		if (desde != null && hasta != null && desde.isAfter(hasta)) {
			throw new ExcepcionDominio("FILTRO_INVALIDO", "La fecha 'desde' es posterior a 'hasta'");
		}
	}
}
