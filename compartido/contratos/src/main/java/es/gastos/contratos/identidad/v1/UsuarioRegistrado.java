package es.gastos.contratos.identidad.v1;

import es.gastos.contratos.EventoIntegracion;

import java.util.UUID;

/**
 * Un usuario se ha registrado. Topic {@code identidad.eventos}.
 * Consumidores: clasificacion (siembra el catálogo inicial del usuario).
 */
public record UsuarioRegistrado(UUID usuarioId, String email, String nombre) implements EventoIntegracion {

	public static final String TIPO = "UsuarioRegistrado";
}
