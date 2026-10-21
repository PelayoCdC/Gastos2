package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

public record CategoriaEliminada(CategoriaId categoriaId, UsuarioId usuarioId, long versionAgregado) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.categoriaId.valor();
	}
}
