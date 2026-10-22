package es.gastos.importacion.aplicacion;

import es.gastos.compartido.pruebas.dobles.PublicadorEventosEnMemoria;
import es.gastos.compartido.pruebas.dobles.UnidadDeTrabajoDirecta;
import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.importacion.aplicacion.respaldo.RestauracionRemota;
import es.gastos.importacion.aplicacion.respaldo.RestaurarRespaldo;
import es.gastos.importacion.dominio.formato.FormatoBancoGuardado;
import es.gastos.importacion.dominio.respaldo.FormatosRestaurados;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurarRespaldoTest {

	private final UsuarioId usuario = new UsuarioId(UUID.randomUUID());

	private final FormatosEnMemoria formatos = new FormatosEnMemoria();

	private final PublicadorEventosEnMemoria publicador = new PublicadorEventosEnMemoria();

	private final List<String> llamadas = new ArrayList<>();

	private final RestaurarRespaldo restaurar = new RestaurarRespaldo(
			(usuario, respaldo, simular) -> {
				this.llamadas.add("movimientos simular=" + simular);
				return new RestauracionRemota.ResumenMovimientos(1, 0, respaldo.transacciones().size(), List.of("de movimientos"));
			},
			(usuario, respaldo, simular) -> {
				this.llamadas.add("clasificacion simular=" + simular);
				return new RestauracionRemota.ResumenClasificacion(2, 3, 0, List.of("de clasificacion"));
			},
			this.formatos, this.publicador, new UnidadDeTrabajoDirecta());

	private final RespaldoV1 respaldo = new RespaldoV1(1, "2026-10-22", null, null, null,
			List.of(new RespaldoV1.Configuracion(UUID.randomUUID().toString(), "Unicaja", 2, 1, 2, 0, null, 3, null)), null, null);

	@Test
	void simularPreguntaATodosYNoCambiaNada() {
		RestaurarRespaldo.Informe informe = this.restaurar.ejecutar(this.usuario, this.respaldo, RestaurarRespaldo.Modo.SIMULAR);

		assertThat(this.llamadas).containsExactly("movimientos simular=true", "clasificacion simular=true");
		assertThat(informe.formatos()).isEqualTo(1);
		assertThat(informe.categorias()).isEqualTo(2);
		assertThat(informe.anomalias()).containsExactly("de movimientos", "de clasificacion");
		assertThat(this.formatos.delUsuario(this.usuario)).isEmpty();
		assertThat(this.publicador.publicados()).isEmpty();
	}

	@Test
	void sustituirRestauraEnOrdenYCambiaLosFormatosDelUsuario() {
		this.restaurar.ejecutar(this.usuario, this.respaldo, RestaurarRespaldo.Modo.SUSTITUIR);

		assertThat(this.llamadas).as("primero se ensaya en todos").containsExactly("movimientos simular=true", "clasificacion simular=true",
				"movimientos simular=false", "clasificacion simular=false");
		assertThat(this.formatos.delUsuario(this.usuario)).singleElement().satisfies(f -> assertThat(f.nombre().valor()).isEqualTo("Unicaja"));
		assertThat(this.publicador.publicados()).hasSize(2);
		assertThat(this.publicador.publicados().getFirst()).isInstanceOf(FormatosRestaurados.class);
		assertThat(this.publicador.publicados().get(1)).isInstanceOf(FormatoBancoGuardado.class);
	}
}
