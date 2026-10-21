package es.gastos.movimientos.dominio;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.Cuenta;
import es.gastos.movimientos.dominio.cuenta.CuentaGuardada;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.cuenta.NombreCuenta;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import es.gastos.movimientos.dominio.transaccion.ClaveDuplicado;
import es.gastos.movimientos.dominio.transaccion.Concepto;
import es.gastos.movimientos.dominio.transaccion.Origen;
import es.gastos.movimientos.dominio.transaccion.Transaccion;
import es.gastos.movimientos.dominio.transaccion.TransaccionCorregida;
import es.gastos.movimientos.dominio.transaccion.TransaccionId;
import es.gastos.movimientos.dominio.transaccion.TransaccionRegistrada;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuentaYTransaccionTest {

	private static final UsuarioId USUARIO = new UsuarioId(UUID.randomUUID());

	private static final LocalDate HOY = LocalDate.of(2026, 10, 21);

	@Test
	void elNombreDeCuentaSeComparaNormalizado() {
		assertThat(new NombreCuenta("  Nómina  ").normalizado()).isEqualTo(new NombreCuenta("NOMINA").normalizado());
		assertThatThrownBy(() -> new NombreCuenta(" ")).isInstanceOf(ExcepcionDominio.class);
	}

	@Test
	void unaCuentaSoloAnunciaCambiosSiCambiaAlgo() {
		Cuenta cuenta = Cuenta.crear(new CuentaId(UUID.randomUUID()), USUARIO, new NombreCuenta("Nómina"), "Unicaja", " ");
		assertThat(cuenta.descripcion()).as("los textos vacíos se guardan como ausentes").isNull();
		cuenta.extraerEventos();

		cuenta.modificar(new NombreCuenta("Nómina"), " Unicaja ", null);
		assertThat(cuenta.extraerEventos()).isEmpty();

		cuenta.modificar(new NombreCuenta("Ahorro"), "Unicaja", null);
		assertThat(cuenta.extraerEventos()).singleElement().isInstanceOfSatisfying(CuentaGuardada.class,
				e -> assertThat(e.versionAgregado()).isEqualTo(2));
	}

	@Test
	void unaTransaccionManualNoTieneSaldoNiDocumentoYAnunciaSuRegistro() {
		Transaccion transaccion = manual("CAFE", "-1.50");

		Transaccion.Datos datos = transaccion.datos();
		assertThat(datos.origen()).isEqualTo(Origen.MANUAL);
		assertThat(datos.saldo()).isNull();
		assertThat(datos.orden()).isZero();
		assertThat(transaccion.extraerEventos()).singleElement().isInstanceOf(TransaccionRegistrada.class);
	}

	@Test
	void laClaveDeDuplicadoIgnoraMayusculasYEspaciosPeroNoTildes() {
		ClaveDuplicado clave = ClaveDuplicado.de(HOY, Importe.de("-1.5"), new Concepto("Café  BAR"));

		assertThat(clave).isEqualTo(ClaveDuplicado.de(HOY, Importe.de("-1.50"), new Concepto(" café bar ")));
		assertThat(clave).isNotEqualTo(ClaveDuplicado.de(HOY, Importe.de("-1.50"), new Concepto("cafe bar")));
		assertThat(clave.valor()).hasSize(64);
	}

	@Test
	void corregirUnaManualRecalculaSuClave() {
		Transaccion transaccion = manual("CAFE", "-1.50");
		transaccion.extraerEventos();
		ClaveDuplicado antes = transaccion.datos().claveDuplicado();

		transaccion.corregir(null, HOY, null, new Concepto("CAFE Y TOSTADA"), "desayuno", Importe.de("-3.20"));

		assertThat(transaccion.datos().claveDuplicado()).isNotEqualTo(antes);
		assertThat(transaccion.extraerEventos()).singleElement().isInstanceOf(TransaccionCorregida.class);
	}

	@Test
	void unaImportadaConservaSuClaveYNoCambiaDeCuentaPorSuCuenta() {
		CuentaId cuenta = new CuentaId(UUID.randomUUID());
		ClaveDuplicado claveOriginal = ClaveDuplicado.de(HOY, Importe.de("-10"), new Concepto("LUPA 21 10 2026"));
		Transaccion importada = Transaccion.reconstituir(USUARIO, new Transaccion.Datos(new TransaccionId(UUID.randomUUID()),
				cuenta, new DocumentoId(UUID.randomUUID()), HOY, HOY, new Concepto("LUPA 21 10 2026"), null, Importe.de("-10"),
				Importe.de("90"), 3, 1, Origen.IMPORTACION, claveOriginal), 1);

		importada.corregir(cuenta, HOY, HOY, new Concepto("LUPA (super)"), "nota", Importe.de("-10"));
		assertThat(importada.datos().claveDuplicado()).isEqualTo(claveOriginal);
		assertThat(importada.datos().saldo()).isEqualTo(Importe.de("90"));

		assertThatThrownBy(() -> importada.corregir(new CuentaId(UUID.randomUUID()), HOY, HOY, new Concepto("x"), null, Importe.de("-10")))
				.isInstanceOf(ExcepcionDominio.class)
				.extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("CUENTA_DE_TRANSACCION_IMPORTADA");
	}

	@Test
	void corregirSinCambiosNoEmiteEvento() {
		Transaccion transaccion = manual("CAFE", "-1.50");
		transaccion.extraerEventos();

		transaccion.corregir(null, HOY, null, new Concepto("CAFE"), null, Importe.de("-1.5"));

		assertThat(transaccion.extraerEventos()).isEmpty();
	}

	private static Transaccion manual(String concepto, String importe) {
		return Transaccion.registrarManual(new TransaccionId(UUID.randomUUID()), USUARIO, null, HOY, null,
				new Concepto(concepto), null, Importe.de(importe));
	}
}
