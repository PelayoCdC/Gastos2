package es.gastos.importacion.dominio.extracto;

import java.util.ArrayList;
import java.util.List;

/**
 * Primeras filas de un extracto como texto, para que el usuario elija las columnas al definir un
 * formato. Todas las filas se rellenan hasta el mismo ancho para pintarlas como una tabla.
 */
public record Previsualizacion(String nombreArchivo, int totalFilas, List<List<String>> filas) {

	public static final int FILAS = 20;

	public static Previsualizacion de(String nombreArchivo, Hoja hoja) {
		int mostradas = Math.min(hoja.numeroFilas(), FILAS);
		int ancho = 0;
		for (int i = 0; i < mostradas; i++) {
			ancho = Math.max(ancho, hoja.fila(i).size());
		}

		List<List<String>> filas = new ArrayList<>();
		for (int i = 0; i < mostradas; i++) {
			List<String> fila = new ArrayList<>(hoja.fila(i).stream().map(Celda::texto).toList());
			while (fila.size() < ancho) {
				fila.add("");
			}
			filas.add(List.copyOf(fila));
		}
		return new Previsualizacion(nombreArchivo, hoja.numeroFilas(), List.copyOf(filas));
	}
}
