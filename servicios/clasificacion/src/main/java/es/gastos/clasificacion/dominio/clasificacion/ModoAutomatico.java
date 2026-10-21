package es.gastos.clasificacion.dominio.clasificacion;

/**
 * Qué reglas aplica la clasificación automática:
 * <ul>
 *   <li>IMPORTACION: al llegar una transacción importada; etiquetas → cascada → categorías.</li>
 *   <li>ETIQUETAS: añadir las etiquetas que coincidan (y la cascada) a lo existente.</li>
 *   <li>CATEGORIAS: categorizar las que no tienen categoría.</li>
 *   <li>REINICIAR: quitar lo automático (o todo, si se fuerza) y volver a aplicar como en IMPORTACION.</li>
 * </ul>
 */
public enum ModoAutomatico {
	IMPORTACION,
	ETIQUETAS,
	CATEGORIAS,
	REINICIAR;

	boolean aplicaEtiquetas() {
		return this != CATEGORIAS;
	}

	boolean aplicaCategorias() {
		return this != ETIQUETAS;
	}
}
