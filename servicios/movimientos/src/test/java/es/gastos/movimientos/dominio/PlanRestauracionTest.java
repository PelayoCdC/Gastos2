package es.gastos.movimientos.dominio;

import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.respaldo.PlanRestauracion;
import es.gastos.movimientos.dominio.respaldo.PlanRestauracion.CuentaRespaldo;
import es.gastos.movimientos.dominio.respaldo.PlanRestauracion.DocumentoRespaldo;
import es.gastos.movimientos.dominio.respaldo.PlanRestauracion.TransaccionRespaldo;
import es.gastos.movimientos.dominio.transaccion.Origen;
import es.gastos.movimientos.dominio.transaccion.Transaccion;
import es.gastos.movimientos.dominio.transaccion.TransaccionRegistrada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanRestauracionTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 22);

	private final UsuarioId usuario = new UsuarioId(UUID.randomUUID());

	private final UUID nomina = UUID.randomUUID();

	private final UUID nominaRepetida = UUID.randomUUID();

	private final UUID conSaldo = UUID.randomUUID();

	private final UUID sinSaldo = UUID.randomUUID();

	@Test
	void conservaLosIdsYFusionaLasCuentasConElMismoNombreNormalizado() {
		PlanRestauracion.Plan plan = this.plan(List.of(
				transaccion(UUID.randomUUID(), "NOMINA", "1500", "2500", this.nominaRepetida, this.conSaldo)));

		assertThat(plan.cuentas()).singleElement().satisfies(c -> {
			assertThat(c.id().valor()).isEqualTo(this.nomina);
			assertThat(c.eventosPendientes()).hasSize(1);
		});
		assertThat(plan.documentos()).extracting(d -> d.id().valor()).containsExactly(this.conSaldo, this.sinSaldo);
		assertThat(plan.documentos().getFirst().cuentaId().valor()).isEqualTo(this.nomina);
		assertThat(plan.transacciones().getFirst().datos().cuentaId().valor()).as("pasa a la cuenta superviviente").isEqualTo(this.nomina);
		assertThat(plan.anomalias()).anyMatch(a -> a.contains("se fusionan"));
	}

	@Test
	void losSaldosA0DeUnDocumentoSinSaldosRealesYDeLasAltasAManoQuedanDesconocidos() {
		PlanRestauracion.Plan plan = this.plan(List.of(
				transaccion(UUID.randomUUID(), "A", "-1", "0", this.nomina, this.conSaldo),
				transaccion(UUID.randomUUID(), "B", "-2", "98.00", this.nomina, this.conSaldo),
				transaccion(UUID.randomUUID(), "C", "-3", "0", this.nomina, this.sinSaldo),
				transaccion(UUID.randomUUID(), "D", "-4", "0", this.nomina, null)));

		List<Transaccion> transacciones = plan.transacciones();
		assertThat(transacciones.get(0).datos().saldo()).as("un 0 entre saldos reales es un 0").isNotNull();
		assertThat(transacciones.get(1).datos().saldo().valor()).isEqualByComparingTo("98");
		assertThat(transacciones.get(2).datos().saldo()).isNull();
		assertThat(transacciones.get(3).datos().saldo()).isNull();
		assertThat(transacciones).allSatisfy(t -> {
			assertThat(t.datos().origen()).isEqualTo(Origen.MIGRACION);
			assertThat(t.eventosPendientes()).singleElement().isInstanceOf(TransaccionRegistrada.class);
		});
		assertThat(plan.anomalias()).anyMatch(a -> a.startsWith("2 transacciones tenian saldo 0"));
	}

	@Test
	void descartaLoQueNoSePuedeRestaurarYQuitaLasReferenciasRotas() {
		UUID repetida = UUID.randomUUID();
		PlanRestauracion.Plan plan = this.plan(List.of(
				transaccion(repetida, "OK", "-1", null, UUID.randomUUID(), UUID.randomUUID()),
				transaccion(repetida, "REPETIDA", "-1", null, null, null),
				new TransaccionRespaldo(UUID.randomUUID(), "SIN FECHA", null, new BigDecimal("-1"), null, 0, 0, null, null, null, null)));

		assertThat(plan.transacciones()).singleElement().satisfies(t -> {
			assertThat(t.datos().cuentaId()).isNull();
			assertThat(t.datos().documentoId()).isNull();
		});
		assertThat(plan.anomalias()).anyMatch(a -> a.contains("cuenta que no esta"))
				.anyMatch(a -> a.contains("documento que no esta"))
				.anyMatch(a -> a.contains("id repetido"))
				.anyMatch(a -> a.contains("no tiene fecha contable"));
	}

	@Test
	void unDocumentoSinFechaTomaLaDeHoy() {
		PlanRestauracion.Plan plan = PlanRestauracion.de(this.usuario, List.of(),
				List.of(new DocumentoRespaldo(UUID.randomUUID(), "Viejo", null, null, null)), List.of(), HOY);

		assertThat(plan.documentos()).singleElement().satisfies(d -> {
			assertThat(d.fecha()).isEqualTo(HOY);
			assertThat(d.nombreArchivo()).isEqualTo("Viejo");
			assertThat(d.cuentaId()).isNull();
		});
	}

	private PlanRestauracion.Plan plan(List<TransaccionRespaldo> transacciones) {
		return PlanRestauracion.de(this.usuario,
				List.of(new CuentaRespaldo(this.nomina, "Nómina", "Unicaja", null), new CuentaRespaldo(this.nominaRepetida, " NOMINA ", null, null)),
				List.of(new DocumentoRespaldo(this.conSaldo, "Julio", "julio.xlsx", LocalDate.of(2026, 7, 31), this.nominaRepetida),
						new DocumentoRespaldo(this.sinSaldo, "Agosto", "agosto.csv", LocalDate.of(2026, 8, 31), this.nomina)),
				transacciones, HOY);
	}

	private static TransaccionRespaldo transaccion(UUID id, String concepto, String importe, String saldo, UUID cuenta, UUID documento) {
		return new TransaccionRespaldo(id, concepto, null, new BigDecimal(importe), saldo == null ? null : new BigDecimal(saldo), 1, 1,
				LocalDate.of(2026, 7, 1), null, cuenta, documento);
	}
}
