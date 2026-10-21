package es.gastos.importacion.dominio;

import es.gastos.importacion.dominio.extracto.Celda;
import es.gastos.importacion.dominio.extracto.Hoja;
import es.gastos.importacion.dominio.extracto.Interpretacion;
import es.gastos.importacion.dominio.extracto.InterpreteExtracto;
import es.gastos.importacion.dominio.extracto.Previsualizacion;
import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.FormatoFecha;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.importacion.dominio.formato.MapeoColumnas;
import es.gastos.importacion.dominio.formato.NombreFormato;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de interpretación portadas de {@code ImportacionService} y de {@code dominio.spec.ts} del móvil. */
class InterpreteExtractoTest {

	/** Fecha;Concepto;Importe;Saldo desde la fila 2, como el CSV de {@code lector-csv.spec.ts}. */
	private static final FormatoBanco FORMATO = formato("dd/MM/yyyy", 2, new MapeoColumnas(1, 2, 0, null, 3));

	@Test
	void separaLasLineasValidasDeLasFilasConErroresConservandoElNumeroDeFila() {
		Hoja hoja = hoja(
				fila("Fecha", "Concepto", "Importe", "Saldo"),
				fila("01/07/2026", " COMPRA LUPA ", "-12,34", "100,00"),
				fila("02/07/2026", "NOMINA", "1.850,00", "1950,00"),
				fila("", "", "", ""),
				fila("03/07/2026", "SIN IMPORTE", "", "10"));

		Interpretacion interpretacion = InterpreteExtracto.interpretar(hoja, FORMATO);

		assertThat(interpretacion.lineas()).hasSize(2);
		Interpretacion.Linea primera = interpretacion.lineas().getFirst();
		assertThat(primera.fila()).isEqualTo(2);
		assertThat(primera.fechaContable()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(primera.concepto()).isEqualTo("COMPRA LUPA");
		assertThat(primera.importe()).isEqualTo(Importe.de("-12.34"));
		assertThat(primera.saldo()).isEqualTo(Importe.de("100"));
		assertThat(interpretacion.lineas().get(1).importe()).isEqualTo(Importe.de("1850"));

		assertThat(interpretacion.errores()).singleElement().satisfies(error -> {
			assertThat(error.fila()).isEqualTo(5);
			assertThat(error.mensaje()).isEqualTo("Fila 5: importe vacio o no numerico");
		});
	}

	@Test
	void lasFilasIdenticasSonDosLineas() {
		// Dos cafés iguales el mismo día son dos movimientos; decidir si alguno ya existía es cosa de movimientos
		Hoja hoja = hoja(fila("x"), fila("05/02/2024", "CAFE", "-1,50", ""), fila("05/02/2024", "CAFE", "-1,50", ""));

		assertThat(InterpreteExtracto.interpretar(hoja, FORMATO).lineas()).hasSize(2);
	}

	@Test
	void unaFechaImposibleEsUnErrorDeFilaYNoSeCorrigeEnSilencio() {
		Hoja hoja = hoja(fila("x"), fila("31/02/2024", "COMPRA", "-1", ""), fila("05.02.2024", "COMPRA", "-1", ""));

		Interpretacion interpretacion = InterpreteExtracto.interpretar(hoja, FORMATO);

		assertThat(interpretacion.lineas()).isEmpty();
		assertThat(interpretacion.errores()).extracting(Interpretacion.ErrorFila::mensaje).containsExactly(
				"Fila 2: fecha con formato incorrecto: '31/02/2024'",
				"Fila 3: fecha con formato incorrecto: '05.02.2024'");
	}

	@Test
	void unOpcionalMalEscritoTambienDescartaLaFila() {
		Hoja hoja = hoja(fila("x"), fila("05/02/2024", "COMPRA", "-1", "mucho"));

		assertThat(InterpreteExtracto.interpretar(hoja, FORMATO).errores()).extracting(Interpretacion.ErrorFila::mensaje)
				.containsExactly("Fila 2: importe no numerico: 'mucho'");
	}

	@Test
	void lasCeldasNumericasYDeFechaDeExcelSeUsanSinReinterpretarElTexto() {
		Hoja hoja = new Hoja(List.of(List.of(),
				List.of(new Celda("5/2/24", null, LocalDate.of(2024, 2, 5)), Celda.texto("RECIBO LUZ"),
						new Celda("-45,6", -45.6, null), Celda.VACIA)));

		Interpretacion.Linea linea = InterpreteExtracto.interpretar(hoja, FORMATO).lineas().getFirst();

		assertThat(linea.fechaContable()).isEqualTo(LocalDate.of(2024, 2, 5));
		assertThat(linea.importe()).isEqualTo(Importe.de("-45.60"));
		assertThat(linea.saldo()).isNull();
	}

	@Test
	void lasColumnasFueraDeLaHojaSeLeenComoVacias() {
		FormatoBanco conFechaValor = formato("dd/MM/yyyy", 1, new MapeoColumnas(1, 2, 0, 9, null));
		Hoja hoja = hoja(fila("05/02/2024", "COMPRA", "-1"));

		assertThat(InterpreteExtracto.interpretar(hoja, conFechaValor).lineas().getFirst().fechaValor()).isNull();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"1.234,56|1234.56",
			"1,234.56|1234.56",
			"-12,5|-12.5",
			"3.00 EUR|3.00",
			"'-1.234.567,89 €'|-1234567.89",
			"+20|20"})
	void interpretaImportesConCualquierConvencionDeSeparadores(String texto, BigDecimal esperado) {
		assertThat(InterpreteExtracto.parsearImporte(texto)).isEqualByComparingTo(esperado);
	}

	@Test
	void unImporteSinCifrasNoEsNumerico() {
		assertThatThrownBy(() -> InterpreteExtracto.parsearImporte("abc"))
				.isInstanceOf(ExcepcionDominio.class).hasMessage("importe no numerico: 'abc'");
	}

	@ParameterizedTest
	@CsvSource({"05/02/2024,dd/MM/yyyy", "5/2/2024,d/M/yyyy", "2024-02-05,yyyy-MM-dd", "05-02-24,dd-MM-yy"})
	void leeLosFormatosDeFechaBancariosHabituales(String texto, String patron) {
		assertThat(new FormatoFecha(patron).leer(texto)).isEqualTo(LocalDate.of(2024, 2, 5));
	}

	@Test
	void unPatronDeFechaInvalidoSeRechazaAlGuardarElFormato() {
		assertThatThrownBy(() -> new FormatoFecha("dd/MM/yyyy{")).isInstanceOf(ExcepcionDominio.class)
				.extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("FORMATO_FECHA_INVALIDO");
		assertThat(new FormatoFecha(" ").patron()).isEqualTo(FormatoFecha.POR_DEFECTO);
	}

	@Test
	void laPrevisualizacionMuestraVeinteFilasDelMismoAncho() {
		List<List<Celda>> filas = new java.util.ArrayList<>();
		filas.add(fila("Fecha", "Concepto", "Importe", "Saldo"));
		for (int i = 0; i < 30; i++) {
			filas.add(fila("01/07/2026", "C" + i));
		}

		Previsualizacion previsualizacion = Previsualizacion.de("extracto.csv", new Hoja(filas));

		assertThat(previsualizacion.totalFilas()).isEqualTo(31);
		assertThat(previsualizacion.filas()).hasSize(Previsualizacion.FILAS).allSatisfy(fila -> assertThat(fila).hasSize(4));
		assertThat(previsualizacion.filas().get(1)).containsExactly("01/07/2026", "C0", "", "");
	}

	private static FormatoBanco formato(String patronFecha, int filaInicio, MapeoColumnas columnas) {
		return FormatoBanco.crear(new FormatoId(UUID.randomUUID()), new UsuarioId(UUID.randomUUID()), new NombreFormato("Banco"),
				filaInicio, columnas, new FormatoFecha(patronFecha));
	}

	private static Hoja hoja(List<Celda>... filas) {
		return new Hoja(Arrays.asList(filas));
	}

	private static List<Celda> fila(String... textos) {
		return Arrays.stream(textos).map(Celda::texto).toList();
	}
}
