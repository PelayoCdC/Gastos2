package es.gastos.contratos.clasificacion.v1;

import es.gastos.contratos.EventoIntegracion;

/**
 * Se han sustituido el catálogo y las clasificaciones del usuario por los de un respaldo
 * (docs/adr/0020). Evento de conjunto: los consumidores olvidan las categorías, etiquetas y
 * clasificaciones que tenían del usuario; después llegan los {@code CategoriaGuardada},
 * {@code EtiquetaGuardada} y {@code TransaccionClasificada} (motivo MIGRACION) restaurados.
 * {@code agregadoId} es el usuario. Consumidores: consultas.
 */
public record ClasificacionRestaurada(int categorias, int etiquetas, int clasificadas) implements EventoIntegracion {

	public static final String TIPO = "ClasificacionRestaurada";
}
