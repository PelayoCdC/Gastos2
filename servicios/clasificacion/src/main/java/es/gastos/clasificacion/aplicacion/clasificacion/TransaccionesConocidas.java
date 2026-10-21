package es.gastos.clasificacion.aplicacion.clasificacion;

import es.gastos.clasificacion.dominio.clasificacion.ClasificacionRepositorio;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.kernel.aplicacion.Propiedad;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Obtiene la clasificación de transacciones del usuario: las que ya conoce este contexto y, si no,
 * consultando a movimientos (read-through) y registrándolas en la copia local. Una transacción que
 * no existe o es de otro usuario es un 404, igual que en movimientos.
 */
final class TransaccionesConocidas {

	private final ClasificacionRepositorio repositorio;

	private final OrigenTransacciones origen;

	TransaccionesConocidas(ClasificacionRepositorio repositorio, OrigenTransacciones origen) {
		this.repositorio = repositorio;
		this.origen = origen;
	}

	ClasificacionTransaccion obtener(UsuarioId usuario, TransaccionId id) {
		var conocida = this.repositorio.buscar(id);
		if (conocida.isPresent()) {
			return Propiedad.exigir(conocida, usuario, "la transaccion", id);
		}
		OrigenTransacciones.HechosTransaccion hechos = this.origen.consultar(usuario, id)
				.orElseThrow(() -> new RecursoNoEncontrado("la transaccion", id));
		return this.registrar(usuario, hechos);
	}

	/** Las del usuario de entre esos ids; las inexistentes o ajenas simplemente no aparecen. */
	List<ClasificacionTransaccion> obtenerVarias(UsuarioId usuario, Collection<TransaccionId> ids) {
		List<ClasificacionTransaccion> encontradas = new ArrayList<>(this.repositorio.buscarVarias(usuario, ids));
		Set<TransaccionId> faltan = new HashSet<>(ids);
		encontradas.forEach(c -> faltan.remove(c.id()));
		if (!faltan.isEmpty()) {
			this.origen.consultarVarias(usuario, faltan).forEach(hechos -> encontradas.add(this.registrar(usuario, hechos)));
		}
		return encontradas;
	}

	private ClasificacionTransaccion registrar(UsuarioId usuario, OrigenTransacciones.HechosTransaccion hechos) {
		// Versión de origen 0: cualquier evento de movimientos que llegue después la actualizará
		ClasificacionTransaccion nueva = ClasificacionTransaccion.conocer(hechos.id(), usuario, hechos.documentoId(), hechos.concepto(), 0);
		this.repositorio.guardar(nueva);
		// Se recarga para seguir con la versión ya persistida (si no, un segundo guardado volvería a insertarla)
		return this.repositorio.buscar(hechos.id()).orElseThrow();
	}
}
