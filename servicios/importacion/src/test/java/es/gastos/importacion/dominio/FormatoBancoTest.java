package es.gastos.importacion.dominio;

import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.FormatoBancoEliminado;
import es.gastos.importacion.dominio.formato.FormatoBancoGuardado;
import es.gastos.importacion.dominio.formato.FormatoFecha;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.importacion.dominio.formato.MapeoColumnas;
import es.gastos.importacion.dominio.formato.NombreFormato;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatoBancoTest {

	private static final MapeoColumnas COLUMNAS = new MapeoColumnas(1, 2, 0, null, 3);

	@Test
	void crearModificarYEliminarAnuncianElEstadoConVersionesConsecutivas() {
		FormatoBanco formato = nuevo();
		formato.modificar(new NombreFormato("Unicaja csv"), 3, COLUMNAS, new FormatoFecha("d/M/yyyy"));
		formato.eliminar();

		assertThat(formato.eventosPendientes()).hasSize(3);
		assertThat(formato.eventosPendientes().get(0)).isInstanceOf(FormatoBancoGuardado.class);
		assertThat(formato.eventosPendientes().get(1)).isInstanceOfSatisfying(FormatoBancoGuardado.class, e -> {
			assertThat(e.versionAgregado()).isEqualTo(2);
			assertThat(e.filaInicio()).isEqualTo(3);
		});
		assertThat(formato.eventosPendientes().get(2)).isInstanceOfSatisfying(FormatoBancoEliminado.class,
				e -> assertThat(e.versionAgregado()).isEqualTo(3));
	}

	@Test
	void modificarSinCambiosNoAnunciaNada() {
		FormatoBanco formato = nuevo();
		formato.modificar(new NombreFormato(" Unicaja "), 2, new MapeoColumnas(1, 2, 0, null, 3), new FormatoFecha(null));

		assertThat(formato.eventosPendientes()).hasSize(1);
	}

	@Test
	void lasColumnasObligatoriasSeExigenAlGuardarYNoAlImportar() {
		assertThatThrownBy(() -> new MapeoColumnas(1, null, 0, null, null))
				.isInstanceOf(ExcepcionDominio.class).hasMessage("Falta la columna de importe");
		assertThatThrownBy(() -> new MapeoColumnas(1, 2, 0, -1, null))
				.isInstanceOf(ExcepcionDominio.class).hasMessage("La columna de fecha valor no puede ser negativa");
	}

	@Test
	void laFilaDeInicioEmpiezaEnUno() {
		assertThatThrownBy(() -> FormatoBanco.crear(new FormatoId(UUID.randomUUID()), new UsuarioId(UUID.randomUUID()),
				new NombreFormato("x"), 0, COLUMNAS, new FormatoFecha(null)))
				.isInstanceOf(ExcepcionDominio.class).extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("FILA_INICIO_INVALIDA");
	}

	private static FormatoBanco nuevo() {
		return FormatoBanco.crear(new FormatoId(UUID.randomUUID()), new UsuarioId(UUID.randomUUID()), new NombreFormato("Unicaja"), 2,
				COLUMNAS, new FormatoFecha(null));
	}
}
