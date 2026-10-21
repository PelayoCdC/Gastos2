package es.gastos.clasificacion.dominio.catalogo;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/** La etiqueta se ha creado o modificado; lleva su estado completo. */
public record EtiquetaGuardada(EtiquetaId etiquetaId, UsuarioId usuarioId, long versionAgregado, NombreCatalogo nombre,
		String descripcion, Ocurrencias ocurrencias, CategoriaId categoriaId, int prioridad) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.etiquetaId.valor();
	}
}
