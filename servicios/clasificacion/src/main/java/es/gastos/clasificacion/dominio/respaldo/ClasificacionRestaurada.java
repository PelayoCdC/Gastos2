package es.gastos.clasificacion.dominio.respaldo;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/**
 * El catálogo y las clasificaciones del usuario se han sustituido por los de un respaldo. Afecta a todo lo
 * del usuario, así que el "agregado" es el propio usuario y no lleva versión.
 */
public record ClasificacionRestaurada(UsuarioId usuarioId, int categorias, int etiquetas, int clasificadas) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.usuarioId.valor();
	}

	@Override
	public long versionAgregado() {
		return 0;
	}
}
