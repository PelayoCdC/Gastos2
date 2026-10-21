package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/** La categoría se ha creado o modificado; lleva su estado completo. */
public record CategoriaGuardada(CategoriaId categoriaId, UsuarioId usuarioId, long versionAgregado, NombreCatalogo nombre,
		Color color, String descripcion, Ocurrencias ocurrencias, int prioridad) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.categoriaId.valor();
	}
}
