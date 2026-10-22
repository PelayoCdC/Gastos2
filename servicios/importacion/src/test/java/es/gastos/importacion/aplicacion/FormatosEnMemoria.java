package es.gastos.importacion.aplicacion;

import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.importacion.dominio.formato.FormatoRepositorio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Doble en memoria del repositorio de formatos para los tests de aplicación. */
class FormatosEnMemoria implements FormatoRepositorio {

	private final Map<FormatoId, FormatoBanco> formatos = new LinkedHashMap<>();

	@Override
	public void guardar(FormatoBanco formato) {
		this.formatos.put(formato.id(), formato);
	}

	@Override
	public Optional<FormatoBanco> buscar(FormatoId id) {
		return Optional.ofNullable(this.formatos.get(id));
	}

	@Override
	public List<FormatoBanco> delUsuario(UsuarioId usuario) {
		return this.formatos.values().stream().filter(f -> f.perteneceA(usuario))
				.sorted(Comparator.comparing(f -> f.nombre().normalizado())).toList();
	}

	@Override
	public boolean existeNombre(UsuarioId usuario, String nombreNormalizado, FormatoId excepto) {
		return this.delUsuario(usuario).stream()
				.anyMatch(f -> f.nombre().normalizado().equals(nombreNormalizado) && !f.id().equals(excepto));
	}

	@Override
	public void eliminar(FormatoBanco formato) {
		this.formatos.remove(formato.id());
	}

	@Override
	public void eliminarDelUsuario(UsuarioId usuario) {
		this.formatos.values().removeIf(f -> f.perteneceA(usuario));
	}

	@Override
	public boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<FormatoId> ids) {
		return ids.stream().map(this.formatos::get).anyMatch(f -> f != null && !f.perteneceA(usuario));
	}
}
