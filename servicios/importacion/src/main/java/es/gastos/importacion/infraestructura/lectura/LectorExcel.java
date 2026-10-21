package es.gastos.importacion.infraestructura.lectura;

import es.gastos.importacion.dominio.extracto.Celda;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lee la primera hoja de un Excel (xlsx o xls) con POI. Cada celda se convierte en una
 * {@link Celda} con el texto que ve el usuario (formateado en español) y, si la celda es numérica o
 * de fecha, también su valor, para no tener que reinterpretar un texto que ya era un número.
 *
 * <p>Las fórmulas se leen por su último valor calculado (el que guardó Excel), sin evaluarlas.
 */
final class LectorExcel {

	private static final Locale ESPANOL = Locale.forLanguageTag("es-ES");

	private LectorExcel() {
	}

	static List<List<Celda>> leer(byte[] contenido) throws IOException {
		DataFormatter formateador = new DataFormatter(ESPANOL);
		formateador.setUseCachedValuesForFormulaCells(true);

		try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
			if (libro.getNumberOfSheets() == 0) {
				return List.of();
			}
			Sheet hoja = libro.getSheetAt(0);
			List<List<Celda>> filas = new ArrayList<>();
			for (int i = 0; i <= hoja.getLastRowNum(); i++) {
				filas.add(fila(hoja.getRow(i), formateador));
			}
			// POI puede materializar filas vacías con formato al final de la hoja
			while (!filas.isEmpty() && filas.getLast().stream().allMatch(Celda::vacia)) {
				filas.removeLast();
			}
			return filas;
		}
	}

	private static List<Celda> fila(Row fila, DataFormatter formateador) {
		if (fila == null || fila.getLastCellNum() <= 0) {
			return List.of();
		}
		List<Celda> celdas = new ArrayList<>();
		for (int j = 0; j < fila.getLastCellNum(); j++) {
			celdas.add(celda(fila.getCell(j), formateador));
		}
		return celdas;
	}

	private static Celda celda(Cell celda, DataFormatter formateador) {
		if (celda == null) {
			return Celda.VACIA;
		}
		String texto = formateador.formatCellValue(celda);
		CellType tipo = celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType() : celda.getCellType();
		if (tipo != CellType.NUMERIC) {
			return Celda.texto(texto);
		}
		if (DateUtil.isCellDateFormatted(celda)) {
			return new Celda(texto, null, celda.getLocalDateTimeCellValue().toLocalDate());
		}
		return new Celda(texto, celda.getNumericCellValue(), null);
	}
}
