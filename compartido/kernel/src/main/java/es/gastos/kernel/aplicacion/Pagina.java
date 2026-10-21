package es.gastos.kernel.aplicacion;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados con un formato JSON estable y propio (no el {@code PageImpl} de Spring,
 * cuya serialización cambia entre versiones y filtraba detalles internos en el back antiguo).
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos) {

	public Pagina {
		contenido = List.copyOf(contenido);
	}

	public static <T> Pagina<T> de(List<T> contenido, PeticionPagina peticion, long totalElementos) {
		return new Pagina<>(contenido, peticion.pagina(), peticion.tamano(), totalElementos);
	}

	public int totalPaginas() {
		return this.tamano == 0 ? 0 : (int) ((this.totalElementos + this.tamano - 1) / this.tamano);
	}

	public <R> Pagina<R> map(Function<? super T, ? extends R> conversion) {
		List<R> convertido = this.contenido.stream().<R>map(conversion).toList();
		return new Pagina<>(convertido, this.pagina, this.tamano, this.totalElementos);
	}
}
