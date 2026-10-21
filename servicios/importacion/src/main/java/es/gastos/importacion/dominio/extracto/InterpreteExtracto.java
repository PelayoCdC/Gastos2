package es.gastos.importacion.dominio.extracto;

import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.MapeoColumnas;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de dominio que traduce la hoja de un banco a movimientos con un {@link FormatoBanco}.
 *
 * <p>Reglas (las mismas que el back antiguo salvo las fechas, ahora estrictas, docs/adr/0016):
 * <ul>
 *   <li>Se lee desde {@code filaInicio}; las filas sin concepto, importe ni fecha se saltan en silencio.</li>
 *   <li>Importe: si la celda es numérica, su valor; si es texto, se quitan los caracteres que no son
 *   dígitos, coma, punto o signo y el <b>último</b> separador es el decimal ("1.234,56" y
 *   "1,234.56" son 1234.56). Un solo punto es siempre decimal ("1.234" es 1.234).</li>
 *   <li>Fecha: si la celda es una fecha de Excel, esa; si es texto, con el patrón del formato.</li>
 *   <li>Una fila con un dato obligatorio vacío o mal escrito, o con uno opcional mal escrito, se
 *   descarta entera y queda como error ("Fila N: ...").</li>
 * </ul>
 */
public final class InterpreteExtracto {

	private InterpreteExtracto() {
	}

	public static Interpretacion interpretar(Hoja hoja, FormatoBanco formato) {
		MapeoColumnas columnas = formato.columnas();
		List<Interpretacion.Linea> lineas = new ArrayList<>();
		List<Interpretacion.ErrorFila> errores = new ArrayList<>();

		for (int i = Math.max(formato.filaInicio() - 1, 0); i < hoja.numeroFilas(); i++) {
			Celda concepto = hoja.celda(i, columnas.concepto());
			Celda importe = hoja.celda(i, columnas.importe());
			Celda fechaContable = hoja.celda(i, columnas.fechaContable());
			if (concepto.vacia() && importe.vacia() && fechaContable.vacia()) {
				continue;
			}

			try {
				lineas.add(new Interpretacion.Linea(i + 1,
						fecha(fechaContable, formato, true),
						fecha(hoja.celda(i, columnas.fechaValor()), formato, false),
						concepto.texto().strip(),
						importe(importe, true),
						importe(hoja.celda(i, columnas.saldo()), false)));
			} catch (ExcepcionDominio e) {
				errores.add(new Interpretacion.ErrorFila(i + 1, "Fila " + (i + 1) + ": " + e.getMessage()));
			}
		}

		return new Interpretacion(lineas, errores);
	}

	static Importe importe(Celda celda, boolean obligatorio) {
		if (celda.numero() != null) {
			return Importe.de(celda.numero());
		}
		if (celda.texto().isBlank()) {
			if (obligatorio) {
				throw new ExcepcionDominio("IMPORTE_INVALIDO", "importe vacio o no numerico");
			}
			return null;
		}
		return Importe.de(parsearImporte(celda.texto()));
	}

	/** Interpreta un importe escrito con cualquier convención de separadores. */
	public static BigDecimal parsearImporte(String texto) {
		String limpio = texto.replaceAll("[^0-9,.\\-]", "");
		int ultimaComa = limpio.lastIndexOf(',');
		int ultimoPunto = limpio.lastIndexOf('.');
		if (ultimaComa >= 0 && ultimoPunto >= 0) {
			limpio = ultimaComa > ultimoPunto
					? limpio.replace(".", "").replace(',', '.')
					: limpio.replace(",", "");
		} else if (ultimaComa >= 0) {
			limpio = limpio.replace(',', '.');
		}

		try {
			return new BigDecimal(limpio);
		} catch (NumberFormatException e) {
			throw new ExcepcionDominio("IMPORTE_INVALIDO", "importe no numerico: '" + texto.strip() + "'");
		}
	}

	private static LocalDate fecha(Celda celda, FormatoBanco formato, boolean obligatoria) {
		if (celda.fecha() != null) {
			return celda.fecha();
		}
		if (celda.texto().isBlank()) {
			if (obligatoria) {
				throw new ExcepcionDominio("FECHA_INVALIDA", "fecha vacia o con formato incorrecto");
			}
			return null;
		}
		try {
			return formato.formatoFecha().leer(celda.texto());
		} catch (DateTimeParseException e) {
			throw new ExcepcionDominio("FECHA_INVALIDA", "fecha con formato incorrecto: '" + celda.texto().strip() + "'");
		}
	}
}
