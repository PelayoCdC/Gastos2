package es.gastos.kernel.aplicacion;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados con un formato JSON estable y propio (no el {@code PageImpl} de Spring,
 * cuya serialización cambia entre versiones y filtraba detalles internos en el back antiguo).
 * {@code totalPaginas} es un componente (y no solo un método) para que viaje en el JSON.
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

	public Pagina {
		contenido = List.copyOf(contenido);
	}

	public Pagina(List<T> contenido, int pagina, int tamano, long totalElementos) {
		this(contenido, pagina, tamano, totalElementos, tamano == 0 ? 0 : (int) ((totalElementos + tamano - 1) / tamano));
	}

	public static <T> Pagina<T> de(List<T> contenido, PeticionPagina peticion, long totalElementos) {
		return new Pagina<>(contenido, peticion.pagina(), peticion.tamano(), totalElementos);
	}

	public <R> Pagina<R> map(Function<? super T, ? extends R> conversion) {
		List<R> convertido = this.contenido.stream().<R>map(conversion).toList();
		return new Pagina<>(convertido, this.pagina, this.tamano, this.totalElementos, this.totalPaginas);
	}
}
