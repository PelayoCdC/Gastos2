package es.gastos.kernel.dominio;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RaizAgregadoTest {

	private static final UsuarioId USUARIO = new UsuarioId(UUID.randomUUID());

	record Renombrado(UUID agregadoId, UsuarioId usuarioId, long versionAgregado, String nombre) implements EventoDominio {
	}

	static final class Ejemplo extends RaizAgregado<UUID> {

		private final UUID id = UUID.randomUUID();

		Ejemplo(long version) {
			super(version);
		}

		void renombrar(String nombre) {
			this.registrar(new Renombrado(this.id, USUARIO, this.siguienteVersion(), nombre));
		}

		@Override
		public UUID id() {
			return this.id;
		}

		@Override
		public UsuarioId propietario() {
			return USUARIO;
		}
	}

	@Test
	void cadaCambioIncrementaLaVersionYLaEstampaEnSuEvento() {
		Ejemplo ejemplo = new Ejemplo(4);

		ejemplo.renombrar("a");
		ejemplo.renombrar("b");

		assertThat(ejemplo.versionCargada()).isEqualTo(4);
		assertThat(ejemplo.version()).isEqualTo(6);
		assertThat(ejemplo.tieneCambios()).isTrue();
		assertThat(ejemplo.eventosPendientes())
				.extracting(EventoDominio::versionAgregado)
				.containsExactly(5L, 6L);
	}

	@Test
	void losEventosSeExtraenUnaSolaVez() {
		Ejemplo ejemplo = new Ejemplo(0);
		ejemplo.renombrar("a");

		assertThat(ejemplo.esNuevo()).isTrue();
		assertThat(ejemplo.extraerEventos()).hasSize(1);
		assertThat(ejemplo.extraerEventos()).isEmpty();
	}

	@Test
	void perteneceASoloSuPropietario() {
		Ejemplo ejemplo = new Ejemplo(0);

		assertThat(ejemplo.perteneceA(USUARIO)).isTrue();
		assertThat(ejemplo.perteneceA(new UsuarioId(UUID.randomUUID()))).isFalse();
	}
}
