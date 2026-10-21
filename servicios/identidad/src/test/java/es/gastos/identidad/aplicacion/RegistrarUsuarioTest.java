package es.gastos.identidad.aplicacion;

import es.gastos.compartido.pruebas.dobles.GeneradorIdsSecuencial;
import es.gastos.compartido.pruebas.dobles.PublicadorEventosEnMemoria;
import es.gastos.compartido.pruebas.dobles.Relojes;
import es.gastos.compartido.pruebas.dobles.UnidadDeTrabajoDirecta;
import es.gastos.identidad.dominio.usuario.UsuarioRegistrado;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.ExcepcionDominio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrarUsuarioTest {

	private final Dobles.UsuariosEnMemoria usuarios = new Dobles.UsuariosEnMemoria();

	private final PublicadorEventosEnMemoria publicador = new PublicadorEventosEnMemoria();

	private final RegistrarUsuario registrar = new RegistrarUsuario(this.usuarios, new Dobles.CifradorFalso(),
			this.publicador, new UnidadDeTrabajoDirecta(), new GeneradorIdsSecuencial(), Relojes.fijo("2026-10-21"));

	@Test
	void registraAlUsuarioConLaContrasenaCifradaYAnunciaElRegistro() {
		PerfilUsuario perfil = this.registrar.ejecutar(new RegistrarUsuario.Comando("Ana", "Ana@Ejemplo.es", "secreto123"));

		assertThat(perfil.email()).isEqualTo("ana@ejemplo.es");
		assertThat(this.usuarios.usuarios.values()).singleElement()
				.satisfies(u -> assertThat(u.hashContrasena().valor()).isEqualTo("hash:secreto123"));
		assertThat(this.publicador.unico(UsuarioRegistrado.class).usuarioId().valor()).isEqualTo(perfil.id());
	}

	@Test
	void unEmailYaRegistradoEsUnConflictoAunqueCambienMayusculasYEspacios() {
		this.registrar.ejecutar(new RegistrarUsuario.Comando("Ana", "ana@ejemplo.es", "secreto123"));

		assertThatThrownBy(() -> this.registrar.ejecutar(new RegistrarUsuario.Comando("Otra", " ANA@ejemplo.es", "secreto456")))
				.isInstanceOf(Conflicto.class)
				.extracting(e -> ((Conflicto) e).codigo()).isEqualTo("EMAIL_YA_REGISTRADO");
		assertThat(this.publicador.deTipo(UsuarioRegistrado.class)).hasSize(1);
	}

	@Test
	void validaLosDatosAntesDeTocarNada() {
		assertThatThrownBy(() -> this.registrar.ejecutar(new RegistrarUsuario.Comando("Ana", "ana@ejemplo.es", "corta")))
				.isInstanceOf(ExcepcionDominio.class);
		assertThat(this.usuarios.usuarios).isEmpty();
		assertThat(this.publicador.publicados()).isEmpty();
	}
}
