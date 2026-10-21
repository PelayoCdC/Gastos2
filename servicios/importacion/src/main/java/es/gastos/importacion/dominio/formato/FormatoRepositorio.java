package es.gastos.importacion.dominio.formato;

import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de formatos de banco. */
public interface FormatoRepositorio {

	void guardar(FormatoBanco formato);

	Optional<FormatoBanco> buscar(FormatoId id);

	/** Los del usuario, por nombre. */
	List<FormatoBanco> delUsuario(UsuarioId usuario);

	boolean existeNombre(UsuarioId usuario, String nombreNormalizado, FormatoId excepto);

	void eliminar(FormatoBanco formato);
}
