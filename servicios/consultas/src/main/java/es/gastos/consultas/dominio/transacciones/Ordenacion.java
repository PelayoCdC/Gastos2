package es.gastos.consultas.dominio.transacciones;

import es.gastos.kernel.dominio.ExcepcionDominio;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Orden de la rejilla. Solo se puede ordenar por los campos de {@link Campo} (lista blanca): el back
 * antiguo pasaba el nombre del campo tal cual a JPA y un campo desconocido acababa en 500.
 *
 * <p>Siempre se desempata por {@code orden} (la fila del extracto) y por id, en la misma dirección:
 * los movimientos de un mismo día salen en el orden del banco y la paginación es estable (el back
 * antiguo no desempataba y una fila podía repetirse o perderse entre dos páginas).
 */
public record Ordenacion(Campo campo, boolean ascendente) {

	public static final Ordenacion POR_DEFECTO = new Ordenacion(Campo.FECHA_CONTABLE, false);

	public enum Campo {
		FECHA_CONTABLE("fechaContable"),
		FECHA_VALOR("fechaValor"),
		IMPORTE("importe"),
		CONCEPTO("concepto"),
		CUENTA("cuenta"),
		CATEGORIA("categoria");

		private final String nombre;

		Campo(String nombre) {
			this.nombre = nombre;
		}

		public String nombre() {
			return this.nombre;
		}
	}

	public Ordenacion {
		Objects.requireNonNull(campo);
	}

	/**
	 * Lee el orden de la API ({@code orden=importe&direccion=ASC}). Sin campo, fecha contable
	 * descendente (lo más reciente arriba); con campo y sin dirección, ascendente.
	 */
	public static Ordenacion de(String campo, String direccion) {
		boolean sinCampo = campo == null || campo.isBlank();
		Campo elegido = sinCampo ? POR_DEFECTO.campo() : Arrays.stream(Campo.values())
				.filter(c -> c.nombre().equals(campo.strip()))
				.findFirst()
				.orElseThrow(() -> new ExcepcionDominio("ORDEN_INVALIDO", "No se puede ordenar por '" + campo + "'; campos: "
						+ Arrays.stream(Campo.values()).map(Campo::nombre).collect(Collectors.joining(", "))));
		if (direccion == null || direccion.isBlank()) {
			return sinCampo ? POR_DEFECTO : new Ordenacion(elegido, true);
		}
		return switch (direccion.strip().toUpperCase(Locale.ROOT)) {
			case "ASC" -> new Ordenacion(elegido, true);
			case "DESC" -> new Ordenacion(elegido, false);
			default -> throw new ExcepcionDominio("ORDEN_INVALIDO", "La direccion es ASC o DESC");
		};
	}
}
