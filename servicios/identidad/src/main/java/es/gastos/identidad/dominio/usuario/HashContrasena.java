package es.gastos.identidad.dominio.usuario;

import java.util.Objects;

/** Hash de la contraseña. Opaco para el dominio: solo el cifrador sabe interpretarlo. */
public record HashContrasena(String valor) {

	public HashContrasena {
		Objects.requireNonNull(valor, "El hash es obligatorio");
	}

	@Override
	public String toString() {
		return "HashContrasena[oculto]";
	}
}
