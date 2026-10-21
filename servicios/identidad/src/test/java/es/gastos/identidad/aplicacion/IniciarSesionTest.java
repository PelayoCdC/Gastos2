package es.gastos.identidad.aplicacion;

import es.gastos.compartido.pruebas.dobles.GeneradorIdsSecuencial;
import es.gastos.compartido.pruebas.dobles.PublicadorEventosEnMemoria;
import es.gastos.compartido.pruebas.dobles.Relojes;
import es.gastos.compartido.pruebas.dobles.UnidadDeTrabajoDirecta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IniciarSesionTest {

	private final Dobles.UsuariosEnMemoria usuarios = new Dobles.UsuariosEnMemoria();

	private final Dobles.CifradorFalso cifrador = new Dobles.CifradorFalso();

	private final IniciarSesion iniciarSesion = new IniciarSesion(this.usuarios, this.cifrador, new Dobles.EmisorFalso(),
			new UnidadDeTrabajoDirecta());

	@BeforeEach
	void registrarAna() {
		new RegistrarUsuario(this.usuarios, this.cifrador, new PublicadorEventosEnMemoria(), new UnidadDeTrabajoDirecta(),
				new GeneradorIdsSecuencial(), Relojes.fijo("2026-10-21"))
				.ejecutar(new RegistrarUsuario.Comando("Ana", "ana@ejemplo.es", "secreto123"));
	}

	@Test
	void conCredencialesCorrectasDevuelveUnTokenBearer() {
		IniciarSesion.Sesion sesion = this.iniciarSesion.ejecutar(new IniciarSesion.Comando(" ANA@ejemplo.es", "secreto123"));

		assertThat(sesion.tipo()).isEqualTo("Bearer");
		assertThat(sesion.token()).isEqualTo("token-de-ana@ejemplo.es");
	}

	@Test
	void contrasenaErroneaEmailDesconocidoOMalFormadoDanElMismoError() {
		assertThatThrownBy(() -> this.iniciarSesion.ejecutar(new IniciarSesion.Comando("ana@ejemplo.es", "otraclave1")))
				.isInstanceOf(CredencialesInvalidas.class);
		assertThatThrownBy(() -> this.iniciarSesion.ejecutar(new IniciarSesion.Comando("nadie@ejemplo.es", "secreto123")))
				.isInstanceOf(CredencialesInvalidas.class);
		assertThatThrownBy(() -> this.iniciarSesion.ejecutar(new IniciarSesion.Comando("no-es-un-email", "secreto123")))
				.isInstanceOf(CredencialesInvalidas.class);
	}

	@Test
	void conUnEmailDesconocidoSeGastaElMismoTrabajoQueComprobandoUnaContrasena() {
		assertThatThrownBy(() -> this.iniciarSesion.ejecutar(new IniciarSesion.Comando("nadie@ejemplo.es", "secreto123")))
				.isInstanceOf(CredencialesInvalidas.class);

		assertThat(this.cifrador.simulaciones).isEqualTo(1);
	}
}
