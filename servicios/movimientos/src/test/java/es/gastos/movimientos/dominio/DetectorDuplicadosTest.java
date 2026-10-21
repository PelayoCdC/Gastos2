package es.gastos.movimientos.dominio;

import es.gastos.kernel.dominio.Importe;
import es.gastos.movimientos.dominio.transaccion.ClaveDuplicado;
import es.gastos.movimientos.dominio.transaccion.Concepto;
import es.gastos.movimientos.dominio.transaccion.DetectorDuplicados;
import es.gastos.movimientos.dominio.transaccion.LineaExtracto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Duplicados por conteo (docs/adr/0015). */
class DetectorDuplicadosTest {

	private static final LocalDate DIA = LocalDate.of(2026, 10, 3);

	private final LineaExtracto cafe1 = linea(1, "CAFE BAR PEPE", "-1.50");

	private final LineaExtracto cafe2 = linea(2, "cafe  bar pepe", "-1.5");

	private final LineaExtracto lupa = linea(3, "COMPRA LUPA", "-45.60");

	@Test
	void dosMovimientosRealesIdenticosEnElMismoExtractoSeImportanLosDos() {
		DetectorDuplicados.Separacion separacion = DetectorDuplicados.separar(List.of(this.cafe1, this.cafe2, this.lupa), Map.of());

		assertThat(separacion.nuevas()).containsExactly(this.cafe1, this.cafe2, this.lupa);
		assertThat(separacion.duplicadas()).isEmpty();
	}

	@Test
	void reimportarElMismoExtractoLosMarcaTodosComoDuplicados() {
		Map<ClaveDuplicado, Long> yaRegistrados = Map.of(this.cafe1.clave(), 2L, this.lupa.clave(), 1L);

		DetectorDuplicados.Separacion separacion = DetectorDuplicados.separar(List.of(this.cafe1, this.cafe2, this.lupa), yaRegistrados);

		assertThat(separacion.nuevas()).isEmpty();
		assertThat(separacion.duplicadas()).containsExactly(this.cafe1, this.cafe2, this.lupa);
	}

	@Test
	void siYaHabiaUnoSoloElPrimeroEsDuplicadoYElSegundoNuevo() {
		DetectorDuplicados.Separacion separacion = DetectorDuplicados.separar(List.of(this.cafe1, this.cafe2),
				Map.of(this.cafe1.clave(), 1L));

		assertThat(separacion.duplicadas()).containsExactly(this.cafe1);
		assertThat(separacion.nuevas()).containsExactly(this.cafe2);
	}

	@Test
	void unExtractoQueSeSolapaSoloImportaLoNuevo() {
		LineaExtracto nueva = linea(4, "GASOLINERA REPSOL", "-60");

		DetectorDuplicados.Separacion separacion = DetectorDuplicados.separar(List.of(this.lupa, nueva),
				Map.of(this.lupa.clave(), 1L));

		assertThat(separacion.nuevas()).containsExactly(nueva);
		assertThat(separacion.duplicadas()).containsExactly(this.lupa);
	}

	private static LineaExtracto linea(int fila, String concepto, String importe) {
		return new LineaExtracto(fila, DIA, DIA, new Concepto(concepto), Importe.de(importe), null);
	}
}
