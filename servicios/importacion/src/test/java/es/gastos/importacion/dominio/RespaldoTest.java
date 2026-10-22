package es.gastos.importacion.dominio;

import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.respaldo.PlanFormatos;
import es.gastos.importacion.dominio.respaldo.RespaldoTraducido;
import es.gastos.importacion.dominio.respaldo.TraductorRespaldoV1;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RespaldoTest {

	private final String cuenta = UUID.randomUUID().toString();

	@Test
	void soloAdmiteLaVersion1() {
		assertThatThrownBy(() -> TraductorRespaldoV1.traducir(new RespaldoV1(2, null, null, null, null, null, null, null)))
				.isInstanceOf(ExcepcionDominio.class).extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("RESPALDO_NO_VALIDO");
	}

	@Test
	void leeIdsYFechasYDescartaLasTransaccionesQueNoSePuedenRestaurar() {
		String buena = UUID.randomUUID().toString();
		RespaldoV1 respaldo = new RespaldoV1(1, "2026-10-22", List.of(new RespaldoV1.Cuenta(this.cuenta, "Diario", null, null),
				new RespaldoV1.Cuenta("no-es-un-uuid", "Rota", null, null)), null, null, null, null, List.of(
				transaccion(buena, "2026-07-01", "-1.50", List.of("tampoco", UUID.randomUUID().toString())),
				transaccion(UUID.randomUUID().toString(), null, "-2", List.of()),
				transaccion(UUID.randomUUID().toString(), "2026-07-02", null, List.of()),
				transaccion(UUID.randomUUID().toString(), "02/07/2026", "-3", List.of())));

		RespaldoTraducido traducido = TraductorRespaldoV1.traducir(respaldo);

		assertThat(traducido.cuentas()).extracting(c -> c.id().toString()).containsExactly(this.cuenta);
		assertThat(traducido.transacciones()).singleElement().satisfies(t -> {
			assertThat(t.id().toString()).isEqualTo(buena);
			assertThat(t.fechaContable()).isEqualTo(LocalDate.of(2026, 7, 1));
			assertThat(t.importe()).isEqualByComparingTo("-1.50");
			assertThat(t.etiquetaIds()).as("la referencia no valida se quita").hasSize(1);
			assertThat(t.cuentaId().toString()).isEqualTo(this.cuenta);
		});
		assertThat(traducido.anomalias()).anyMatch(a -> a.contains("no-es-un-uuid"))
				.anyMatch(a -> a.contains("fecha no valida (02/07/2026)"))
				.anyMatch(a -> a.startsWith("3 transacciones sin id, fecha contable o importe"));
	}

	@Test
	void losFormatosIncompletosORepetidosSeDescartanYUnaFechaRaraPasaAlFormatoPorDefecto() {
		UsuarioId usuario = new UsuarioId(UUID.randomUUID());
		PlanFormatos.Plan plan = PlanFormatos.de(usuario, List.of(
				new RespaldoV1.Configuracion(UUID.randomUUID().toString(), "Unicaja", 2, 1, 2, 0, null, 3, "dd/MM/yyyy{"),
				new RespaldoV1.Configuracion(UUID.randomUUID().toString(), " UNICAJA ", 2, 1, 2, 0, null, null, null),
				new RespaldoV1.Configuracion(UUID.randomUUID().toString(), "Sin importe", 0, 1, null, 0, null, null, null)));

		assertThat(plan.formatos()).singleElement().satisfies(f -> {
			assertThat(f.nombre().valor()).isEqualTo("Unicaja");
			assertThat(f.formatoFecha().patron()).isEqualTo("dd/MM/yyyy");
			assertThat(f.eventosPendientes()).hasSize(1);
		});
		assertThat(plan.formatos()).extracting(FormatoBanco::filaInicio).containsExactly(2);
		assertThat(plan.anomalias()).hasSize(3)
				.anyMatch(a -> a.contains("formato de fecha no valido"))
				.anyMatch(a -> a.contains("repite el nombre"))
				.anyMatch(a -> a.contains("Falta la columna de importe"));
	}

	private RespaldoV1.Transaccion transaccion(String id, String fecha, String importe, List<String> etiquetas) {
		return new RespaldoV1.Transaccion(id, "CONCEPTO", null, importe == null ? null : new BigDecimal(importe), BigDecimal.ZERO, 1, 1,
				fecha, null, this.cuenta, null, null, etiquetas);
	}
}
