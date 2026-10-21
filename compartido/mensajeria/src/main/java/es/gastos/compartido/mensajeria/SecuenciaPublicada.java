package es.gastos.compartido.mensajeria;

import java.util.OptionalLong;

/**
 * Última secuencia de outbox escrita por el hilo actual. La capa web la devuelve en la cabecera
 * {@code Gastos-Secuencia} para que el cliente pueda "leer lo que acaba de escribir" en consultas
 * (docs/adr/0011).
 */
public final class SecuenciaPublicada {

	private static final ThreadLocal<Long> ULTIMA = new ThreadLocal<>();

	private SecuenciaPublicada() {
	}

	static void registrar(long secuencia) {
		Long actual = ULTIMA.get();
		if (actual == null || secuencia > actual) {
			ULTIMA.set(secuencia);
		}
	}

	/** Devuelve la última secuencia registrada en este hilo y la olvida. */
	public static OptionalLong consumir() {
		Long ultima = ULTIMA.get();
		ULTIMA.remove();
		return ultima == null ? OptionalLong.empty() : OptionalLong.of(ultima);
	}
}
