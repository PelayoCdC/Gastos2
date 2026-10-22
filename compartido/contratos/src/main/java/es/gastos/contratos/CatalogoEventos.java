package es.gastos.contratos;

import es.gastos.contratos.clasificacion.v1.CategoriaEliminada;
import es.gastos.contratos.clasificacion.v1.ClasificacionRestaurada;
import es.gastos.contratos.clasificacion.v1.CategoriaGuardada;
import es.gastos.contratos.clasificacion.v1.EtiquetaEliminada;
import es.gastos.contratos.clasificacion.v1.EtiquetaGuardada;
import es.gastos.contratos.clasificacion.v1.TransaccionClasificada;
import es.gastos.contratos.identidad.v1.UsuarioRegistrado;
import es.gastos.contratos.importacion.v1.FormatoBancoEliminado;
import es.gastos.contratos.importacion.v1.FormatoBancoGuardado;
import es.gastos.contratos.importacion.v1.FormatosRestaurados;
import es.gastos.contratos.movimientos.v1.CuentaEliminada;
import es.gastos.contratos.movimientos.v1.CuentaGuardada;
import es.gastos.contratos.movimientos.v1.DocumentoEliminado;
import es.gastos.contratos.movimientos.v1.DocumentoReasignado;
import es.gastos.contratos.movimientos.v1.DocumentoRegistrado;
import es.gastos.contratos.movimientos.v1.DocumentoRenombrado;
import es.gastos.contratos.movimientos.v1.MovimientosRestaurados;
import es.gastos.contratos.movimientos.v1.TransaccionCorregida;
import es.gastos.contratos.movimientos.v1.TransaccionEliminada;
import es.gastos.contratos.movimientos.v1.TransaccionRegistrada;

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
			new Entrada(UsuarioRegistrado.TIPO, 1, Topics.IDENTIDAD, UsuarioRegistrado.class),

			new Entrada(CuentaGuardada.TIPO, 1, Topics.MOVIMIENTOS, CuentaGuardada.class),
			new Entrada(CuentaEliminada.TIPO, 1, Topics.MOVIMIENTOS, CuentaEliminada.class),
			new Entrada(DocumentoRegistrado.TIPO, 1, Topics.MOVIMIENTOS, DocumentoRegistrado.class),
			new Entrada(DocumentoRenombrado.TIPO, 1, Topics.MOVIMIENTOS, DocumentoRenombrado.class),
			new Entrada(DocumentoReasignado.TIPO, 1, Topics.MOVIMIENTOS, DocumentoReasignado.class),
			new Entrada(DocumentoEliminado.TIPO, 1, Topics.MOVIMIENTOS, DocumentoEliminado.class),
			new Entrada(TransaccionRegistrada.TIPO, 1, Topics.MOVIMIENTOS, TransaccionRegistrada.class),
			new Entrada(TransaccionCorregida.TIPO, 1, Topics.MOVIMIENTOS, TransaccionCorregida.class),
			new Entrada(TransaccionEliminada.TIPO, 1, Topics.MOVIMIENTOS, TransaccionEliminada.class),
			new Entrada(MovimientosRestaurados.TIPO, 1, Topics.MOVIMIENTOS, MovimientosRestaurados.class),

			new Entrada(CategoriaGuardada.TIPO, 1, Topics.CLASIFICACION, CategoriaGuardada.class),
			new Entrada(CategoriaEliminada.TIPO, 1, Topics.CLASIFICACION, CategoriaEliminada.class),
			new Entrada(EtiquetaGuardada.TIPO, 1, Topics.CLASIFICACION, EtiquetaGuardada.class),
			new Entrada(EtiquetaEliminada.TIPO, 1, Topics.CLASIFICACION, EtiquetaEliminada.class),
			new Entrada(TransaccionClasificada.TIPO, 1, Topics.CLASIFICACION, TransaccionClasificada.class),
			new Entrada(ClasificacionRestaurada.TIPO, 1, Topics.CLASIFICACION, ClasificacionRestaurada.class),

			new Entrada(FormatoBancoGuardado.TIPO, 1, Topics.IMPORTACION, FormatoBancoGuardado.class),
			new Entrada(FormatoBancoEliminado.TIPO, 1, Topics.IMPORTACION, FormatoBancoEliminado.class),
			new Entrada(FormatosRestaurados.TIPO, 1, Topics.IMPORTACION, FormatosRestaurados.class)
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
