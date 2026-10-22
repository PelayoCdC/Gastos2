package es.gastos.consultas.aplicacion.consistencia;

import es.gastos.consultas.dominio.consistencia.SecuenciasEsperadas;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.dominio.UsuarioId;

import java.time.Duration;

/**
 * Espera, con un tope, a que las proyecciones hayan procesado los eventos que el cliente acaba de
 * provocar (docs/adr/0011). Comprueba cada 50 ms durante 3 s como mucho; si no llegan, la consulta se
 * responde igualmente y el cliente lo sabe por {@code Gastos-Desfase}.
 *
 * <p>Se cuenta por intentos y no por reloj para que el tope no dependa de cuánto tarde cada lectura
 * de la posición (y para poder probarlo sin dormir).
 */
@CasoDeUso
public class EsperarConsistencia {

	public static final Duration INTERVALO = Duration.ofMillis(50);

	public static final Duration ESPERA_MAXIMA = Duration.ofSeconds(3);

	private static final long INTENTOS = ESPERA_MAXIMA.toMillis() / INTERVALO.toMillis();

	private final PosicionesLectura posiciones;

	private final Pausa pausa;

	public EsperarConsistencia(PosicionesLectura posiciones, Pausa pausa) {
		this.posiciones = posiciones;
		this.pausa = pausa;
	}

	/** Devuelve si la proyección ha alcanzado lo esperado (las secuencias de contextos que no proyecta no cuentan). */
	public boolean esperar(UsuarioId usuario, SecuenciasEsperadas esperadas) {
		SecuenciasEsperadas relevantes = esperadas.soloDe(this.posiciones::proyecta);
		if (relevantes.vacia()) {
			return true;
		}
		for (long intento = 0; intento < INTENTOS; intento++) {
			if (relevantes.alcanzadasPor(this.posiciones.de(usuario))) {
				return true;
			}
			this.pausa.pausar(INTERVALO);
		}
		return relevantes.alcanzadasPor(this.posiciones.de(usuario));
	}
}
