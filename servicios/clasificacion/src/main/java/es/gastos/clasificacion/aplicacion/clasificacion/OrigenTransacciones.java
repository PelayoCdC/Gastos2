package es.gastos.clasificacion.aplicacion.clasificacion;

import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para el <i>read-through</i> (docs/adr/0002): consultar a movimientos una
 * transacción que este contexto todavía no conoce porque su evento aún no ha llegado (el front la
 * acaba de crear y la clasifica en la petición siguiente). Solo devuelve transacciones del usuario.
 */
public interface OrigenTransacciones {

	Optional<HechosTransaccion> consultar(UsuarioId usuario, TransaccionId id);

	List<HechosTransaccion> consultarVarias(UsuarioId usuario, Collection<TransaccionId> ids);

	record HechosTransaccion(TransaccionId id, UUID documentoId, String concepto) {
	}
}
