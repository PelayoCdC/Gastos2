package es.gastos.importacion.infraestructura.lectura;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Port de {@code LectorCsvTest} del back antiguo y de {@code lector-csv.spec.ts} del móvil. */
class LectorCsvTest {

	@Test
	void detectaLaExtensionCsvSinImportarMayusculas() {
		assertThat(LectorCsv.esCsv("extracto.CSV")).isTrue();
		assertThat(LectorCsv.esCsv("extracto.csv")).isTrue();
		assertThat(LectorCsv.esCsv("extracto.xlsx")).isFalse();
		assertThat(LectorCsv.esCsv(null)).isFalse();
	}

	@Test
	void parseaSeparadorPuntoYComaConComillas() {
		List<List<String>> filas = LectorCsv.leer(utf8(
				"Fecha;Concepto;Importe\n01/07/2026;\"CONCEPTO; CON SEPARADOR\";-12,34\n02/07/2026;\"COMILLA \"\" DOBLE\";5\n"));

		assertThat(filas).hasSize(3);
		assertThat(filas.get(1)).containsExactly("01/07/2026", "CONCEPTO; CON SEPARADOR", "-12,34");
		assertThat(filas.get(2).get(1)).isEqualTo("COMILLA \" DOBLE");
	}

	@Test
	void parseaSeparadorComa() {
		assertThat(LectorCsv.leer(utf8("Fecha,Concepto,Importe\n01/07/2026,FARMACIA,-12.34\n")).get(1).get(1)).isEqualTo("FARMACIA");
	}

	@Test
	void parseaTabuladoresSaltosDeLineaDentroDeComillasYFinalesDeLineaWindows() {
		List<List<String>> filas = LectorCsv.leer(utf8("Fecha\tConcepto\r\n01/07/2026\t\"DOS\r\nLINEAS\"\r\n\r\n\t\r\n"));

		assertThat(filas).hasSize(2);
		assertThat(filas.get(1)).containsExactly("01/07/2026", "DOS\r\nLINEAS");
	}

	@Test
	void decodificaWindows1252ConTildes() {
		byte[] contenido = "Fecha;Concepto\n01/07/2026;PELUQUERÍA JOSÉ\n".getBytes(Charset.forName("windows-1252"));

		assertThat(LectorCsv.leer(contenido).get(1).get(1)).isEqualTo("PELUQUERÍA JOSÉ");
	}

	@Test
	void decodificaUtf8ConBom() {
		byte[] sinBom = utf8("Fecha;Concepto\n01/07/2026;CAFETERÍA\n");
		byte[] conBom = new byte[sinBom.length + 3];
		conBom[0] = (byte) 0xEF;
		conBom[1] = (byte) 0xBB;
		conBom[2] = (byte) 0xBF;
		System.arraycopy(sinBom, 0, conBom, 3, sinBom.length);

		List<List<String>> filas = LectorCsv.leer(conBom);

		assertThat(filas.get(0).get(0)).isEqualTo("Fecha");
		assertThat(filas.get(1).get(1)).isEqualTo("CAFETERÍA");
	}

	@Test
	void laUltimaFilaSinSaltoDeLineaTambienCuenta() {
		assertThat(LectorCsv.leer(utf8("a;b\nc;d"))).containsExactly(List.of("a", "b"), List.of("c", "d"));
	}

	private static byte[] utf8(String texto) {
		return texto.getBytes(StandardCharsets.UTF_8);
	}
}
