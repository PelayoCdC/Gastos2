package es.gastos.kernel.ids;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Uuid7Test {

	@Test
	void generaUuidsVersion7ConVarianteRfc() {
		UUID uuid = Uuid7.generar();

		assertThat(uuid.version()).isEqualTo(7);
		assertThat(uuid.variant()).isEqualTo(2);
	}

	@Test
	void incluyeLaMarcaDeTiempoEnLosBitsAltos() {
		long antes = System.currentTimeMillis();
		UUID uuid = Uuid7.generar();
		long despues = System.currentTimeMillis();

		long marca = uuid.getMostSignificantBits() >>> 16;
		assertThat(marca).isBetween(antes, despues);
	}

	@Test
	void noSeRepiten() {
		Set<UUID> generados = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			generados.add(Uuid7.generar());
		}

		assertThat(generados).hasSize(10_000);
	}
}
