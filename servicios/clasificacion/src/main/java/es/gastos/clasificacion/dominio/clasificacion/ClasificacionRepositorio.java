package es.gastos.clasificacion.dominio.clasificacion;

import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;

/**
 * Puerto de persistencia de las clasificaciones de transacciones, con las operaciones por conjuntos
 * que acompañan a los borrados del catálogo (una sentencia, un evento de conjunto).
 */
public interface ClasificacionRepositorio {

	/** Quita la categoría a todas las transacciones que la tenían; devuelve cuántas. */
	int desasignarCategoria(CategoriaId categoria);

	/** Quita la etiqueta de todas las transacciones que la tenían; devuelve cuántas. */
	int quitarEtiqueta(EtiquetaId etiqueta);
}
