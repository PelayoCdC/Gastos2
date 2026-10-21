package es.gastos.movimientos.dominio.transaccion;

import es.gastos.kernel.dominio.Importe;
import es.gastos.kernel.dominio.Normalizador;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Huella para reconocer un movimiento ya registrado al reimportar extractos que se solapan.
 *
 * <p>Se forma con {@code fecha | importe | concepto} (concepto en minúsculas y con los espacios
 * colapsados, como en el back antiguo; sin quitar tildes) y se guarda como SHA-256 en hexadecimal
 * para que el índice tenga tamaño fijo. El importe usa {@link Importe#comoTexto()}, estable al
 * contrario que el {@code float} antiguo (que escribía "1850.0" o "1.0E7").
 */
public record ClaveDuplicado(String valor) {

	public static ClaveDuplicado de(LocalDate fechaContable, Importe importe, Concepto concepto) {
		String texto = fechaContable + "|" + importe.comoTexto() + "|"
				+ Normalizador.colapsarEspacios(concepto.valor().toLowerCase(Locale.ROOT));
		try {
			byte[] huella = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
			return new ClaveDuplicado(HexFormat.of().formatHex(huella));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 siempre existe en la JVM", e);
		}
	}
}
