package es.gastos.contratos;

import es.gastos.contratos.identidad.v1.UsuarioRegistrado;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registro de todos los eventos de integración: tipo y versión → clase y topic.
 *
 * <p>Los consumidores lo usan para convertir el campo {@code datos} del sobre en el record
 * correcto sin depender de información de tipos dentro del JSON (que acoplaría el contrato a
 * Jackson y a los nombres de paquete Java). Un test comprueba que cada tipo registrado aquí está
 * documentado en {@code docs/eventos/catalogo.md}.
 */
public final class CatalogoEventos {

	/** Entrada del catálogo: un tipo de evento en una versión concreta. */
	public record Entrada(String tipo, int version, String topic, Class<? extends EventoIntegracion> clase) {

		String clave() {
			return clave(this.tipo, this.version);
		}

		static String clave(String tipo, int version) {
			return tipo + "@" + version;
		}
	}

	private static final List<Entrada> ENTRADAS = List.of(
			new Entrada(UsuarioRegistrado.TIPO, 1, Topics.IDENTIDAD, UsuarioRegistrado.class)
	);

	private static final Map<String, Entrada> POR_CLAVE = ENTRADAS.stream()
			.collect(Collectors.toUnmodifiableMap(Entrada::clave, Function.identity()));

	private static final Map<Class<?>, Entrada> POR_CLASE = ENTRADAS.stream()
			.collect(Collectors.toUnmodifiableMap(Entrada::clase, Function.identity()));

	private CatalogoEventos() {
	}

	public static List<Entrada> todos() {
		return ENTRADAS;
	}

	public static Optional<Entrada> buscar(String tipo, int version) {
		return Optional.ofNullable(POR_CLAVE.get(Entrada.clave(tipo, version)));
	}

	public static Entrada de(Class<? extends EventoIntegracion> clase) {
		Entrada entrada = POR_CLASE.get(clase);
		if (entrada == null) {
			throw new IllegalArgumentException("Evento no registrado en el catalogo: " + clase.getName());
		}
		return entrada;
	}
}
