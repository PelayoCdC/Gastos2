package es.gastos.identidad.infraestructura.eventos;

import es.gastos.compartido.mensajeria.TraductorEventos;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.identidad.dominio.usuario.UsuarioRegistrado;
import es.gastos.kernel.dominio.EventoDominio;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Traduce los eventos de dominio de identidad a su lenguaje publicado (contratos identidad.v1). */
@Component
class TraductorEventosIdentidad implements TraductorEventos {

	@Override
	public Optional<EventoIntegracion> traducir(EventoDominio evento) {
		return switch (evento) {
			case UsuarioRegistrado registrado -> Optional.of(new es.gastos.contratos.identidad.v1.UsuarioRegistrado(
					registrado.usuarioId().valor(), registrado.email().valor(), registrado.nombre().valor()));
			default -> Optional.empty();
		};
	}
}
