package es.gastos.importacion.dominio.extracto;

import java.util.List;

/**
 * Contenido de un extracto como filas de celdas (fila 0 = primera fila del fichero). Las filas
 * pueden tener longitudes distintas y estar vacías; lo que falta se lee como {@link Celda#VACIA}.
 */
public record Hoja(List<List<Celda>> filas) {

	public Hoja {
		filas = filas.stream().map(List::copyOf).toList();
	}

	public int numeroFilas() {
		return this.filas.size();
	}

	public Celda celda(int fila, Integer columna) {
		if (columna == null || fila < 0 || fila >= this.filas.size()) {
			return Celda.VACIA;
		}
		List<Celda> celdas = this.filas.get(fila);
		return columna < 0 || columna >= celdas.size() ? Celda.VACIA : celdas.get(columna);
	}

	public List<Celda> fila(int fila) {
		return fila < 0 || fila >= this.filas.size() ? List.of() : this.filas.get(fila);
	}
}
