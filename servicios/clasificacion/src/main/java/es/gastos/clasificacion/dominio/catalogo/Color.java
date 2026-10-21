package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.ExcepcionDominio;

import java.util.Locale;
import java.util.regex.Pattern;

/** Color de una categoría en los gráficos, como {@code #rrggbb} en minúsculas. */
public record Color(String valor) {

	private static final Pattern FORMATO = Pattern.compile("^#[0-9a-f]{6}$");

	public Color {
		valor = valor == null ? null : valor.strip().toLowerCase(Locale.ROOT);
		if (valor == null || !FORMATO.matcher(valor).matches()) {
			throw new ExcepcionDominio("COLOR_INVALIDO", "El color debe tener la forma #rrggbb");
		}
	}

	/** El color es opcional: un texto vacío o nulo significa "sin color". */
	public static Color opcional(String texto) {
		return texto == null || texto.isBlank() ? null : new Color(texto);
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
