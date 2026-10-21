package es.gastos.compartido.mensajeria;

import es.gastos.contratos.identidad.v1.UsuarioRegistrado;
import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Optional;
import java.util.UUID;

/** Aplicación mínima para probar la mensajería de punta a punta (outbox → relay → Kafka). */
@SpringBootApplication
public class AplicacionPruebaMensajeria {

	/** Evento de dominio de prueba. */
	public record AlguienSeRegistro(UUID agregadoId, UsuarioId usuarioId, long versionAgregado, String email)
			implements EventoDominio {
	}

	/** Evento de dominio que no se publica fuera (el traductor lo descarta). */
	public record CambioInterno(UUID agregadoId, UsuarioId usuarioId, long versionAgregado) implements EventoDominio {
	}

	@Bean
	TraductorEventos traductorDePrueba() {
		return evento -> switch (evento) {
			case AlguienSeRegistro registro ->
					Optional.of(new UsuarioRegistrado(registro.usuarioId().valor(), registro.email(), "Prueba"));
			default -> Optional.empty();
		};
	}
}
