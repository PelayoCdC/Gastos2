package es.gastos.kernel.aplicacion;

import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.Optional;

/**
 * Comprobación de propiedad para los casos de uso: un agregado que no existe y uno que pertenece a
 * otro usuario son indistinguibles para quien pregunta (los dos dan {@link RecursoNoEncontrado}, 404).
 */
public final class Propiedad {

	private Propiedad() {
	}

	public static <A extends RaizAgregado<?>> A exigir(Optional<A> agregado, UsuarioId usuario, String recurso, Object id) {
		return agregado.filter(a -> a.perteneceA(usuario)).orElseThrow(() -> new RecursoNoEncontrado(recurso, id));
	}
}
