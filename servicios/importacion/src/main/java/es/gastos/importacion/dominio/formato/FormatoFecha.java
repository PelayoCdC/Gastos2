package es.gastos.importacion.dominio.formato;

import es.gastos.kernel.dominio.ExcepcionDominio;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Patrón de fecha de un banco ({@code dd/MM/yyyy} por defecto), interpretado de forma <b>estricta</b>
 * (docs/adr/0016): "31/02/2024" es un error y no se convierte en silencio en el 29 de febrero, que es
 * lo que hacía el resolver por defecto de Java en el back antiguo. Una fecha imposible casi siempre
 * delata un formato mal configurado (día y mes cambiados).
 */
public record FormatoFecha(String patron) {

	public static final String POR_DEFECTO = "dd/MM/yyyy";

	public FormatoFecha {
		patron = patron == null || patron.isBlank() ? POR_DEFECTO : patron.strip();
		try {
			formateador(patron);
		} catch (IllegalArgumentException e) {
			throw new ExcepcionDominio("FORMATO_FECHA_INVALIDO", "El formato de fecha no es valido: " + patron);
		}
	}

	/** Lee una fecha con el patrón; lanza {@link DateTimeParseException} si no encaja o no existe. */
	public LocalDate leer(String texto) {
		return LocalDate.parse(texto.strip(), formateador(this.patron));
	}

	/**
	 * En modo estricto "y" es el año de la era y exige la era; "u" es el año a secas, que es lo que
	 * quiere decir cualquiera que escribe "yyyy". Por eso se sustituye antes de compilar el patrón.
	 */
	private static DateTimeFormatter formateador(String patron) {
		return DateTimeFormatter.ofPattern(patron.replace('y', 'u')).withResolverStyle(ResolverStyle.STRICT);
	}

	@Override
	public String toString() {
		return this.patron;
	}
}
