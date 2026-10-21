package es.gastos.identidad.dominio.usuario;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioTest {

	@Test
	void elEmailSeGuardaEnFormaCanonica() {
		assertThat(new Email("  Ana@Ejemplo.ES ")).isEqualTo(new Email("ana@ejemplo.es"));
	}

	@Test
	void rechazaEmailsSinFormatoONombresVacios() {
		assertThatThrownBy(() -> new Email("ana")).isInstanceOf(ExcepcionDominio.class).hasMessageContaining("formato");
		assertThatThrownBy(() -> new Email("ana @ejemplo.es")).isInstanceOf(ExcepcionDominio.class);
		assertThatThrownBy(() -> new Nombre("  ")).isInstanceOf(ExcepcionDominio.class);
		assertThat(new Nombre("  Ana  ").valor()).isEqualTo("Ana");
	}

	@Test
	void laContrasenaExigeOchoCaracteresYNoSeMuestra() {
		assertThatThrownBy(() -> new ContrasenaEnClaro("corta")).isInstanceOf(ExcepcionDominio.class)
				.extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("CONTRASENA_INVALIDA");
		assertThatThrownBy(() -> new ContrasenaEnClaro("x".repeat(73))).isInstanceOf(ExcepcionDominio.class);
		assertThat(new ContrasenaEnClaro("secreto123")).hasToString("********");
	}

	@Test
	void registrarEmiteUsuarioRegistradoConLaVersionUno() {
		UsuarioId id = new UsuarioId(UUID.randomUUID());

		Usuario usuario = Usuario.registrar(id, new Email("ana@ejemplo.es"), new Nombre("Ana"),
				new HashContrasena("hash"), Instant.parse("2026-10-21T10:00:00Z"));

		assertThat(usuario.esNuevo()).isTrue();
		assertThat(usuario.version()).isEqualTo(1);
		assertThat(usuario.extraerEventos()).containsExactly(
				new UsuarioRegistrado(id, 1, new Email("ana@ejemplo.es"), new Nombre("Ana")));
	}
}
