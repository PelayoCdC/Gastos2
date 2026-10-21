package es.gastos.clasificacion.aplicacion.clasificacion;

import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Clasificación de una transacción tal y como se devuelve: con nombres y el origen de cada asignación. */
public record DatosClasificacion(UUID transaccionId, CategoriaAsignada categoria, List<EtiquetaAsignada> etiquetas) {

	public record CategoriaAsignada(UUID id, String nombre, String color, String origen) {
	}

	public record EtiquetaAsignada(UUID id, String nombre, String origen) {
	}

	static DatosClasificacion de(ClasificacionTransaccion clasificacion, Map<CategoriaId, Categoria> categorias,
			Map<EtiquetaId, Etiqueta> etiquetas) {
		Categoria categoria = clasificacion.categoria() == null ? null : categorias.get(clasificacion.categoria());
		return new DatosClasificacion(clasificacion.id().valor(),
				categoria == null ? null : new CategoriaAsignada(categoria.id().valor(), categoria.nombre().valor(),
						categoria.color() == null ? null : categoria.color().valor(), clasificacion.origenCategoria().name()),
				clasificacion.etiquetas().entrySet().stream()
						.map(asignacion -> {
							Etiqueta etiqueta = etiquetas.get(asignacion.getKey());
							return etiqueta == null ? null
									: new EtiquetaAsignada(etiqueta.id().valor(), etiqueta.nombre().valor(), asignacion.getValue().name());
						})
						.filter(Objects::nonNull)
						.toList());
	}
}
