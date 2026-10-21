package es.gastos.compartido.mensajeria;

import es.gastos.contratos.identidad.v1.UsuarioRegistrado;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LectorSobresTest {

	private final LectorSobres lector = new LectorSobres(JsonMapper.builder().build());

	@Test
	void convierteLosDatosAlRecordDelCatalogo() {
		String mensaje = """
				{"eventoId":"0192f3a1-7c4e-7a10-8b2c-3d4e5f607182","tipo":"UsuarioRegistrado","contexto":"identidad",
				 "version":1,"secuencia":7,"ocurridoEn":"2026-10-21T10:00:00Z",
				 "usuarioId":"0192f3a1-7c4e-7a10-8b2c-3d4e5f607183","agregadoId":"0192f3a1-7c4e-7a10-8b2c-3d4e5f607183",
				 "versionAgregado":1,
				 "datos":{"usuarioId":"0192f3a1-7c4e-7a10-8b2c-3d4e5f607183","email":"ana@ejemplo.es","nombre":"Ana"}}
				""";

		var sobre = this.lector.leer(mensaje).orElseThrow();

		assertThat(sobre.secuencia()).isEqualTo(7);
		assertThat(sobre.datos()).isInstanceOf(UsuarioRegistrado.class);
		assertThat(((UsuarioRegistrado) sobre.datos()).email()).isEqualTo("ana@ejemplo.es");
	}

	@Test
	void ignoraLosTiposYVersionesQueNoConoce() {
		String mensaje = """
				{"eventoId":"0192f3a1-7c4e-7a10-8b2c-3d4e5f607182","tipo":"UsuarioRegistrado","contexto":"identidad",
				 "version":99,"secuencia":7,"ocurridoEn":"2026-10-21T10:00:00Z",
				 "usuarioId":"0192f3a1-7c4e-7a10-8b2c-3d4e5f607183","agregadoId":"0192f3a1-7c4e-7a10-8b2c-3d4e5f607183",
				 "versionAgregado":1,"datos":{"loQueSea":true}}
				""";

		assertThat(this.lector.leer(mensaje)).isEmpty();
	}
}
