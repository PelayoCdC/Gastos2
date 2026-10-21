package es.gastos.kernel.ids;

import es.gastos.kernel.aplicacion.GeneradorIds;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generador de UUID versión 7 (RFC 9562): 48 bits de marca de tiempo en milisegundos seguidos de
 * bits aleatorios. Son únicos como un UUIDv4 pero crecen con el tiempo, así que se insertan al
 * final del índice primario en vez de en posiciones aleatorias.
 */
public final class Uuid7 implements GeneradorIds {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	@Override
	public UUID nuevo() {
		return generar();
	}

	public static UUID generar() {
		long milisegundos = System.currentTimeMillis();
		byte[] aleatorio = new byte[10];
		ALEATORIO.nextBytes(aleatorio);

		// 48 bits de tiempo | versión 7 (4 bits) | 12 bits aleatorios
		long altos = (milisegundos << 16)
				| 0x7000L
				| ((aleatorio[0] & 0x0FL) << 8)
				| (aleatorio[1] & 0xFFL);

		// variante RFC 4122 (bits 10) | 62 bits aleatorios
		long bajos = 0x8000000000000000L | ((aleatorio[2] & 0x3FL) << 56);
		for (int i = 3; i < 10; i++) {
			bajos |= (aleatorio[i] & 0xFFL) << (8 * (9 - i));
		}

		return new UUID(altos, bajos);
	}
}
