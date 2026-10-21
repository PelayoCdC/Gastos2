package es.gastos.clasificacion.dominio.clasificacion;

import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.UUID;

/** La clasificación de la transacción ha cambiado; lleva el estado completo (categoría y etiquetas en orden). */
public record TransaccionClasificada(TransaccionId transaccionId, UsuarioId usuarioId, long versionAgregado,
		CategoriaId categoria, List<EtiquetaId> etiquetas, MotivoClasificacion motivo) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.transaccionId.valor();
	}
}
