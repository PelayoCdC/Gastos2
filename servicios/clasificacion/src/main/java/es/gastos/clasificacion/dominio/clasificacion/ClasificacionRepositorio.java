package es.gastos.clasificacion.dominio.clasificacion;

import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.kernel.dominio.ClaveConcepto;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de las clasificaciones de transacciones, con las operaciones por conjuntos
 * que acompañan a los borrados (una sentencia, un evento de conjunto).
 */
public interface ClasificacionRepositorio {

	void guardar(ClasificacionTransaccion clasificacion);

	Optional<ClasificacionTransaccion> buscar(TransaccionId id);

	/** Las del usuario de entre esos ids (las ajenas o desconocidas no aparecen). */
	List<ClasificacionTransaccion> buscarVarias(UsuarioId usuario, Collection<TransaccionId> ids);

	/** Todas las transacciones conocidas del usuario (para la clasificación automática en bloque). */
	List<ClasificacionTransaccion> delUsuario(UsuarioId usuario);

	/** Las del usuario con la misma clave de concepto que la indicada, salvo ella misma. */
	List<ClasificacionTransaccion> similares(UsuarioId usuario, ClaveConcepto clave, TransaccionId excepto);

	/** La transacción ya no existe en movimientos: se olvida su clasificación. */
	void olvidar(TransaccionId id);

	/** El documento (y sus transacciones) ya no existe: se olvidan sus clasificaciones; devuelve cuántas. */
	int olvidarDocumento(UUID documentoId);

	/** Quita la categoría a todas las transacciones que la tenían; devuelve cuántas. */
	int desasignarCategoria(CategoriaId categoria);

	/** Quita la etiqueta de todas las transacciones que la tenían; devuelve cuántas. */
	int quitarEtiqueta(EtiquetaId etiqueta);

	/** Olvida todas las transacciones del usuario con sus clasificaciones (restaurar un respaldo). */
	void olvidarDelUsuario(UsuarioId usuario);

	/** Si alguna de esas transacciones ya es de otro usuario. */
	boolean algunaDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids);
}
