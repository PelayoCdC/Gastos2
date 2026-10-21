package es.gastos.kernel.dominio;

import java.util.regex.Pattern;

/**
 * Clave de comparación de un concepto bancario: dos movimientos con la misma clave se consideran
 * "similares" (mismo comercio o mismo cargo periódico).
 *
 * <p>Los bancos incrustan la fecha de la operación en el concepto ({@code "GOGALIA 04 02 2024"},
 * {@code "T/BAR 25-12-2019"}); la clave la elimina, además de normalizar tildes, mayúsculas y
 * espacios. La usan la propagación de clasificaciones y los análisis de top-conceptos y gastos
 * recurrentes, por eso está en el kernel.
 *
 * @param valor clave normalizada; vacía si el concepto no tenía texto significativo
 */
public record ClaveConcepto(String valor) {

	/**
	 * Fecha d-m-a (1-2, 1-2 y 2-4 dígitos) o a-m-d (4, 1-2 y 1-2). Las retrorreferencias {@code \2}
	 * y {@code \3} exigen el mismo separador en toda la fecha, así "12-05 2024" no se considera
	 * una fecha. Es el mismo patrón que usaban el back antiguo y la app móvil.
	 */
	static final Pattern PATRON_FECHA = Pattern.compile(
			"\\b(\\d{1,2}([\\s/.-])\\d{1,2}\\2\\d{2,4}|\\d{4}([\\s/.-])\\d{1,2}\\3\\d{1,2})\\b");

	public ClaveConcepto {
		valor = valor == null ? "" : valor;
	}

	public static ClaveConcepto de(String concepto) {
		if (concepto == null) {
			return new ClaveConcepto("");
		}

		String sinFechas = PATRON_FECHA.matcher(Normalizador.normalizar(concepto)).replaceAll(" ");
		return new ClaveConcepto(Normalizador.colapsarEspacios(sinFechas));
	}

	public boolean esVacia() {
		return this.valor.isEmpty();
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
