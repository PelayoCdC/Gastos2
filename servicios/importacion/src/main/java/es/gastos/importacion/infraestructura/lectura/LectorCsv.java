package es.gastos.importacion.infraestructura.lectura;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lee un CSV de banco como filas de texto. Port de {@code LectorCsv} del back antiguo (y de
 * {@code lector-csv.ts} del móvil), con el mismo comportamiento:
 * <ul>
 *   <li>Charset: UTF-8 (con o sin BOM) y, si no decodifica, windows-1252, que es como exportan
 *   muchos bancos españoles.</li>
 *   <li>Separador: {@code ;}, {@code ,} o tabulador, el más frecuente en las 5 primeras líneas
 *   (en caso de empate, el primero de esa lista).</li>
 *   <li>Comillas dobles con separadores o saltos de línea dentro y {@code ""} como comilla escapada.</li>
 *   <li>Se quitan las filas totalmente vacías del final.</li>
 * </ul>
 * Ya no pasa por un Workbook de POI en memoria: produce directamente las filas que necesita la
 * {@code Hoja} del dominio.
 */
final class LectorCsv {

	private static final char[] SEPARADORES_CANDIDATOS = {';', ',', '\t'};

	private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

	private LectorCsv() {
	}

	static boolean esCsv(String nombreArchivo) {
		return nombreArchivo != null && nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".csv");
	}

	static List<List<String>> leer(byte[] contenido) {
		String texto = decodificar(contenido);
		return parsear(texto, detectarSeparador(texto));
	}

	private static String decodificar(byte[] contenido) {
		int inicio = contenido.length >= 3 && (contenido[0] & 0xFF) == 0xEF && (contenido[1] & 0xFF) == 0xBB
				&& (contenido[2] & 0xFF) == 0xBF ? 3 : 0;
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(contenido, inicio, contenido.length - inicio))
					.toString();
		} catch (CharacterCodingException e) {
			return new String(contenido, inicio, contenido.length - inicio, WINDOWS_1252);
		}
	}

	private static char detectarSeparador(String texto) {
		String[] primerasLineas = texto.split("\r?\n", 6);
		char mejor = ';';
		long mejorCuenta = -1;
		for (char candidato : SEPARADORES_CANDIDATOS) {
			long cuenta = 0;
			for (int i = 0; i < Math.min(primerasLineas.length, 5); i++) {
				cuenta += primerasLineas[i].chars().filter(c -> c == candidato).count();
			}
			if (cuenta > mejorCuenta) {
				mejorCuenta = cuenta;
				mejor = candidato;
			}
		}
		return mejor;
	}

	private static List<List<String>> parsear(String texto, char separador) {
		List<List<String>> filas = new ArrayList<>();
		List<String> fila = new ArrayList<>();
		StringBuilder campo = new StringBuilder();
		boolean entreComillas = false;

		for (int i = 0; i < texto.length(); i++) {
			char caracter = texto.charAt(i);
			if (entreComillas) {
				if (caracter == '"') {
					if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
						campo.append('"');
						i++;
					} else {
						entreComillas = false;
					}
				} else {
					campo.append(caracter);
				}
			} else if (caracter == '"') {
				entreComillas = true;
			} else if (caracter == separador) {
				fila.add(campo.toString());
				campo.setLength(0);
			} else if (caracter == '\n' || caracter == '\r') {
				if (caracter == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
					i++;
				}
				fila.add(campo.toString());
				campo.setLength(0);
				filas.add(fila);
				fila = new ArrayList<>();
			} else {
				campo.append(caracter);
			}
		}

		if (!campo.isEmpty() || !fila.isEmpty()) {
			fila.add(campo.toString());
			filas.add(fila);
		}

		while (!filas.isEmpty() && filas.getLast().stream().allMatch(String::isBlank)) {
			filas.removeLast();
		}
		return filas;
	}
}
