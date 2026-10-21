package es.gastos.identidad.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.identidad.aplicacion.ConsultarPerfil;
import es.gastos.identidad.aplicacion.IniciarSesion;
import es.gastos.identidad.aplicacion.PerfilUsuario;
import es.gastos.identidad.aplicacion.RegistrarUsuario;
import es.gastos.kernel.dominio.UsuarioId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Adaptador REST de identidad. Rutas públicas: {@code POST /registros} y {@code POST /sesiones}
 * (configuradas en {@code gastos.seguridad.rutas-publicas}); el resto exige token.
 */
@RestController
class IdentidadController {

	record PeticionRegistro(@NotBlank String nombre, @NotBlank String email, @NotBlank String contrasena) {
	}

	record PeticionSesion(@NotBlank String email, @NotBlank String contrasena) {
	}

	private final RegistrarUsuario registrarUsuario;

	private final IniciarSesion iniciarSesion;

	private final ConsultarPerfil consultarPerfil;

	IdentidadController(RegistrarUsuario registrarUsuario, IniciarSesion iniciarSesion, ConsultarPerfil consultarPerfil) {
		this.registrarUsuario = registrarUsuario;
		this.iniciarSesion = iniciarSesion;
		this.consultarPerfil = consultarPerfil;
	}

	@PostMapping("/registros")
	ResponseEntity<PerfilUsuario> registrar(@Valid @RequestBody PeticionRegistro peticion) {
		PerfilUsuario perfil = this.registrarUsuario.ejecutar(
				new RegistrarUsuario.Comando(peticion.nombre(), peticion.email(), peticion.contrasena()));
		return ResponseEntity.created(URI.create("/usuarios/yo")).body(perfil);
	}

	@PostMapping("/sesiones")
	IniciarSesion.Sesion iniciarSesion(@Valid @RequestBody PeticionSesion peticion) {
		return this.iniciarSesion.ejecutar(new IniciarSesion.Comando(peticion.email(), peticion.contrasena()));
	}

	@GetMapping("/usuarios/yo")
	PerfilUsuario yo(@UsuarioActual UsuarioId usuario) {
		return this.consultarPerfil.ejecutar(usuario);
	}
}
