package es.gastos.importacion.infraestructura.lectura;

import es.gastos.importacion.dominio.extracto.Celda;
import es.gastos.importacion.dominio.extracto.Hoja;
import es.gastos.kernel.dominio.ExcepcionDominio;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LectorHojasArchivoTest {

	private final LectorHojasArchivo lector = new LectorHojasArchivo();

	@Test
	void leeUnXlsxConCeldasDeTextoNumeroFechaYFormula() throws IOException {
		Hoja hoja = this.lector.leer("extracto.xlsx", excel(new XSSFWorkbook()));

		assertThat(hoja.numeroFilas()).as("sin las filas vacías del final").isEqualTo(3);
		assertThat(hoja.celda(0, 1)).isEqualTo(Celda.texto("Concepto"));
		assertThat(hoja.celda(1, 0).fecha()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(hoja.celda(1, 1).texto()).isEqualTo("FARMACIA TEST S.L.");
		assertThat(hoja.celda(1, 2).numero()).isEqualTo(-12.34);
		assertThat(hoja.celda(1, 2).texto()).as("texto formateado en español").isEqualTo("-12,34");
		assertThat(hoja.celda(2, 2).numero()).as("valor calculado de la fórmula").isCloseTo(-45.6, within(1e-9));
		assertThat(hoja.celda(2, 0).texto()).isEqualTo("02/07/2026");
		assertThat(hoja.celda(2, 0).fecha()).as("una fecha escrita como texto la interpreta el formato").isNull();
	}

	@Test
	void leeTambienElFormatoXlsAntiguo() throws IOException {
		Hoja hoja = this.lector.leer("extracto.xls", excel(new HSSFWorkbook()));

		assertThat(hoja.celda(1, 2).numero()).isEqualTo(-12.34);
	}

	@Test
	void unCsvSoloTraeTexto() {
		Hoja hoja = this.lector.leer("EXTRACTO.CSV", "Fecha;Importe\n01/07/2026;-12,34\n".getBytes(StandardCharsets.UTF_8));

		assertThat(hoja.celda(1, 1)).isEqualTo(Celda.texto("-12,34"));
	}

	@Test
	void unFicheroQueNoEsExcelNiCsvEsUnErrorDelCliente() {
		assertThatThrownBy(() -> this.lector.leer("extracto.pdf", "%PDF-1.7 no es una hoja".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(ExcepcionDominio.class)
				.extracting(e -> ((ExcepcionDominio) e).codigo()).isEqualTo("FICHERO_ILEGIBLE");
	}

	/** Como {@code excelDePrueba()} de FlujoImportacionTest, con una fecha real y una fórmula. */
	private static byte[] excel(Workbook libro) throws IOException {
		try (libro; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			CellStyle fecha = libro.createCellStyle();
			fecha.setDataFormat(libro.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
			Sheet hoja = libro.createSheet("Movimientos");

			Row cabecera = hoja.createRow(0);
			cabecera.createCell(0).setCellValue("Fecha");
			cabecera.createCell(1).setCellValue("Concepto");
			cabecera.createCell(2).setCellValue("Importe");

			Row fila1 = hoja.createRow(1);
			fila1.createCell(0).setCellValue(LocalDate.of(2026, 7, 1));
			fila1.getCell(0).setCellStyle(fecha);
			fila1.createCell(1).setCellValue("FARMACIA TEST S.L.");
			fila1.createCell(2).setCellValue(-12.34);

			Row fila2 = hoja.createRow(2);
			fila2.createCell(0).setCellValue("02/07/2026");
			fila2.createCell(1).setCellValue("SUPERMERCADO TEST");
			fila2.createCell(2).setCellFormula("-40-5.6");
			libro.getCreationHelper().createFormulaEvaluator().evaluateAll();

			hoja.createRow(3).createCell(0).setCellValue("  ");
			hoja.createRow(4);

			libro.write(salida);
			return salida.toByteArray();
		}
	}
}
