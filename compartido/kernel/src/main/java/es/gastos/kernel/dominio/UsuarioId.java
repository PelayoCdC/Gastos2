package es.gastos.kernel.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidad del usuario propietario de los datos.
 *
 * <p>Todos los contextos son multiusuario y todos los agregados pertenecen a un usuario: es el
 * concepto más compartido del sistema, por eso vive en el kernel. Su origen es el {@code sub} del
 * JWT que emite el contexto de identidad; ningún servicio lo acepta del cliente.
 */
public record UsuarioId(UUID valor) {

	public UsuarioId {
		Objects.requireNonNull(valor, "El id de usuario es obligatorio");
	}

	public static UsuarioId de(String texto) {
		try {
			return new UsuarioId(UUID.fromString(texto));
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new ExcepcionDominio("USUARIO_ID_INVALIDO", "Id de usuario no valido: " + texto);
		}
	}

	@Override
	public String toString() {
		return this.valor.toString();
	}
}
