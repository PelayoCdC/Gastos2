package es.gastos.identidad.aplicacion;

import es.gastos.identidad.dominio.usuario.UsuarioRepositorio;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import es.gastos.kernel.dominio.UsuarioId;

/** Caso de uso: datos del usuario autenticado. */
@CasoDeUso
public class ConsultarPerfil {

	private final UsuarioRepositorio usuarios;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public ConsultarPerfil(UsuarioRepositorio usuarios, UnidadDeTrabajo unidadDeTrabajo) {
		this.usuarios = usuarios;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public PerfilUsuario ejecutar(UsuarioId usuario) {
		return this.unidadDeTrabajo.ejecutarLectura(() -> this.usuarios.buscarPorId(usuario))
				.map(PerfilUsuario::de)
				.orElseThrow(() -> new RecursoNoEncontrado("el usuario", usuario));
	}
}
