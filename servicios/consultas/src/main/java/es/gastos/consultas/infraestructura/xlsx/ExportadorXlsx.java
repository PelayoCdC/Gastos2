package es.gastos.consultas.infraestructura.xlsx;

import es.gastos.consultas.aplicacion.consulta.FilaTransaccion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Genera el Excel de la exportación de transacciones. Mantiene las 7 columnas del back antiguo en
 * el mismo orden (quien tenga fórmulas o macros sobre ese Excel no nota el cambio) y añade al final
 * fecha valor, nota y documento.
 *
 * <p>A diferencia del antiguo, las fechas son fechas de Excel (no texto) y los importes son números
 * con dos decimales; un saldo desconocido queda vacío, no a 0. Se escribe en streaming
 * ({@link SXSSFWorkbook}) para no tener la hoja entera en memoria.
 */
@Component
public class ExportadorXlsx {

	public static final String TIPO = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	static final List<String> CABECERAS = List.of("Fecha", "Concepto", "Cuenta", "Categoria", "Etiquetas", "Importe", "Saldo",
			"Fecha valor", "Nota", "Documento");

	private static final int[] ANCHOS = {12, 50, 24, 20, 30, 12, 12, 12, 30, 30};

	public byte[] exportar(List<FilaTransaccion> filas) {
		// Cerrar el libro borra los ficheros temporales que usa SXSSF para el streaming
		try (SXSSFWorkbook libro = new SXSSFWorkbook(200); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Sheet hoja = libro.createSheet("Transacciones");
			CellStyle cabecera = libro.createCellStyle();
			Font negrita = libro.createFont();
			negrita.setBold(true);
			cabecera.setFont(negrita);
			CellStyle fecha = libro.createCellStyle();
			fecha.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy"));
			CellStyle dinero = libro.createCellStyle();
			dinero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));

			Row primera = hoja.createRow(0);
			for (int i = 0; i < CABECERAS.size(); i++) {
				Cell celda = primera.createCell(i);
				celda.setCellValue(CABECERAS.get(i));
				celda.setCellStyle(cabecera);
				hoja.setColumnWidth(i, ANCHOS[i] * 256);
			}
			hoja.createFreezePane(0, 1);

			int numero = 1;
			for (FilaTransaccion t : filas) {
				Row fila = hoja.createRow(numero++);
				fecha(fila, 0, t.fechaContable(), fecha);
				texto(fila, 1, t.concepto());
				texto(fila, 2, t.cuenta() == null ? null : t.cuenta().nombre());
				texto(fila, 3, t.categoria() == null ? null : t.categoria().nombre());
				texto(fila, 4, t.etiquetas().stream().map(FilaTransaccion.Referencia::nombre).collect(Collectors.joining(", ")));
				dinero(fila, 5, t.importe(), dinero);
				dinero(fila, 6, t.saldo(), dinero);
				fecha(fila, 7, t.fechaValor(), fecha);
				texto(fila, 8, t.nota());
				texto(fila, 9, t.documento() == null ? null : t.documento().nombre());
			}

			libro.write(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar el Excel", e);
		}
	}

	private static void texto(Row fila, int columna, String valor) {
		if (valor != null && !valor.isEmpty()) {
			fila.createCell(columna).setCellValue(valor);
		}
	}

	private static void fecha(Row fila, int columna, LocalDate valor, CellStyle estilo) {
		if (valor != null) {
			Cell celda = fila.createCell(columna);
			celda.setCellValue(valor);
			celda.setCellStyle(estilo);
		}
	}

	private static void dinero(Row fila, int columna, BigDecimal valor, CellStyle estilo) {
		if (valor != null) {
			Cell celda = fila.createCell(columna);
			celda.setCellValue(valor.doubleValue());
			celda.setCellStyle(estilo);
		}
	}
}
