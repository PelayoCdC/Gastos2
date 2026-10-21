package es.gastos.clasificacion.dominio.clasificacion;

import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.kernel.dominio.ClaveConcepto;
import es.gastos.kernel.dominio.Normalizador;
import es.gastos.kernel.dominio.RaizAgregado;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Clasificación de una transacción: su categoría (como mucho una) y sus etiquetas (en orden), cada
 * una con su {@link OrigenAsignacion}, más la copia mínima de la transacción que hace falta para
 * clasificarla (concepto, clave de concepto y documento), que solo cambian los eventos de movimientos.
 *
 * <p>Cada operación que cambia la categoría o las etiquetas emite un único
 * {@link TransaccionClasificada} con el estado completo; si solo cambian los orígenes (por ejemplo,
 * el usuario confirma a mano lo que ya puso una regla) se guarda sin evento, porque para los demás
 * contextos la clasificación es la misma.
 */
public final class ClasificacionTransaccion extends RaizAgregado<TransaccionId> {

	/** Qué hizo la clasificación automática con esta transacción (para los recuentos de la respuesta). */
	public record Resultado(boolean etiquetada, boolean categorizada) {
	}

	private record Foto(CategoriaId categoria, OrigenAsignacion origenCategoria, Map<EtiquetaId, OrigenAsignacion> etiquetas,
			boolean autoClasificada) {
	}

	private final TransaccionId id;

	private final UsuarioId propietario;

	private UUID documentoId;

	private String concepto;

	private ClaveConcepto claveConcepto;

	private long versionOrigen;

	private CategoriaId categoria;

	private OrigenAsignacion origenCategoria;

	private final LinkedHashMap<EtiquetaId, OrigenAsignacion> etiquetas = new LinkedHashMap<>();

	private boolean autoClasificada;

	private ClasificacionTransaccion(TransaccionId id, UsuarioId propietario, UUID documentoId, String concepto,
			long versionOrigen, long version) {
		super(version);
		this.id = Objects.requireNonNull(id);
		this.propietario = Objects.requireNonNull(propietario);
		this.documentoId = documentoId;
		this.concepto = concepto == null ? "" : concepto;
		this.claveConcepto = ClaveConcepto.de(this.concepto);
		this.versionOrigen = versionOrigen;
	}

	/**
	 * Primera vez que este contexto sabe de la transacción (por su evento de registro o por una
	 * consulta directa a movimientos). Aún sin clasificar, así que no hay evento; sí se versiona para
	 * que se guarde.
	 */
	public static ClasificacionTransaccion conocer(TransaccionId id, UsuarioId propietario, UUID documentoId, String concepto,
			long versionOrigen) {
		ClasificacionTransaccion clasificacion = new ClasificacionTransaccion(id, propietario, documentoId, concepto, versionOrigen, 0);
		clasificacion.siguienteVersion();
		return clasificacion;
	}

	public static ClasificacionTransaccion reconstituir(TransaccionId id, UsuarioId propietario, UUID documentoId, String concepto,
			long versionOrigen, CategoriaId categoria, OrigenAsignacion origenCategoria, Map<EtiquetaId, OrigenAsignacion> etiquetas,
			boolean autoClasificada, long version) {
		ClasificacionTransaccion clasificacion = new ClasificacionTransaccion(id, propietario, documentoId, concepto, versionOrigen, version);
		clasificacion.categoria = categoria;
		clasificacion.origenCategoria = categoria == null ? null : origenCategoria;
		clasificacion.etiquetas.putAll(etiquetas);
		clasificacion.autoClasificada = autoClasificada;
		return clasificacion;
	}

	/** Actualiza la copia con los hechos de movimientos; los eventos antiguos o repetidos se ignoran. */
	public void sincronizarOrigen(String nuevoConcepto, UUID nuevoDocumento, long nuevaVersionOrigen) {
		if (nuevaVersionOrigen <= this.versionOrigen) {
			return;
		}
		this.concepto = nuevoConcepto == null ? "" : nuevoConcepto;
		this.claveConcepto = ClaveConcepto.de(this.concepto);
		this.documentoId = nuevoDocumento;
		this.versionOrigen = nuevaVersionOrigen;
		this.siguienteVersion();
	}

	/**
	 * Aplica las reglas del catálogo según el modo (ver {@link ModoAutomatico}). En REINICIAR quita
	 * antes lo automático, o todo si {@code forzar} (docs/adr/0017). Nunca pisa una categoría existente.
	 */
	public Resultado clasificarAutomaticamente(MotorClasificacion motor, ModoAutomatico modo, boolean forzar) {
		Foto antes = this.foto();
		if (modo == ModoAutomatico.REINICIAR) {
			this.reiniciar(forzar);
		}

		String conceptoNormalizado = Normalizador.normalizar(this.concepto);
		boolean etiquetada = false;
		boolean categorizada = false;

		if (modo.aplicaEtiquetas()) {
			for (EtiquetaId etiqueta : motor.etiquetasQueCasan(conceptoNormalizado)) {
				etiquetada |= this.etiquetas.putIfAbsent(etiqueta, OrigenAsignacion.AUTOMATICA) == null;
			}
			if (this.categoria == null) {
				Optional<CategoriaId> cascada = motor.cascada(this.etiquetas.keySet());
				if (cascada.isPresent()) {
					this.fijarCategoria(cascada.get(), OrigenAsignacion.CASCADA);
					etiquetada = true;
				}
			}
		}

		if (modo.aplicaCategorias() && this.categoria == null) {
			Optional<CategoriaId> porOcurrencia = motor.categoriaQueCasa(conceptoNormalizado);
			if (porOcurrencia.isPresent()) {
				this.fijarCategoria(porOcurrencia.get(), OrigenAsignacion.AUTOMATICA);
				categorizada = true;
			}
		}

		if (modo == ModoAutomatico.IMPORTACION) {
			this.autoClasificada = true;
		}

		this.anunciarSiCambio(antes, modo == ModoAutomatico.REINICIAR ? MotivoClasificacion.REINICIO : MotivoClasificacion.AUTOMATICA);
		return new Resultado(etiquetada, categorizada);
	}

	/** El usuario fija a mano la clasificación completa: sustituye categoría y etiquetas. */
	public boolean asignarManualmente(CategoriaId nuevaCategoria, List<EtiquetaId> nuevasEtiquetas) {
		Foto antes = this.foto();
		this.fijarCategoria(nuevaCategoria, OrigenAsignacion.MANUAL);
		this.etiquetas.clear();
		nuevasEtiquetas.forEach(etiqueta -> this.etiquetas.putIfAbsent(etiqueta, OrigenAsignacion.MANUAL));
		return this.anunciarSiCambio(antes, MotivoClasificacion.MANUAL);
	}

	/**
	 * Copia la clasificación de una transacción similar: su categoría sustituye a la propia (si tiene)
	 * y sus etiquetas se añaden a las propias. Devuelve si cambió algo.
	 */
	public boolean propagarDesde(ClasificacionTransaccion base) {
		Foto antes = this.foto();
		if (base.categoria != null && !base.categoria.equals(this.categoria)) {
			this.fijarCategoria(base.categoria, OrigenAsignacion.PROPAGACION);
		}
		base.etiquetas.keySet().forEach(etiqueta -> this.etiquetas.putIfAbsent(etiqueta, OrigenAsignacion.PROPAGACION));
		return this.anunciarSiCambio(antes, MotivoClasificacion.PROPAGACION);
	}

	/**
	 * Aplicación en bloque: la categoría, si se indica, sustituye a la existente (si no, no se toca);
	 * las etiquetas se añaden o, con {@code sustituir}, reemplazan a las existentes (lista vacía =
	 * quitarlas todas).
	 */
	public boolean aplicarEnBloque(CategoriaId nuevaCategoria, List<EtiquetaId> nuevasEtiquetas, boolean sustituir) {
		Foto antes = this.foto();
		if (nuevaCategoria != null && !nuevaCategoria.equals(this.categoria)) {
			this.fijarCategoria(nuevaCategoria, OrigenAsignacion.MASIVA);
		}
		if (sustituir) {
			this.etiquetas.keySet().retainAll(nuevasEtiquetas);
		}
		nuevasEtiquetas.forEach(etiqueta -> this.etiquetas.putIfAbsent(etiqueta, OrigenAsignacion.MASIVA));
		return this.anunciarSiCambio(antes, MotivoClasificacion.MASIVA);
	}

	private void reiniciar(boolean forzar) {
		if (forzar || (this.origenCategoria != null && this.origenCategoria.esAutomatica())) {
			this.fijarCategoria(null, null);
		}
		this.etiquetas.entrySet().removeIf(asignacion -> forzar || asignacion.getValue().esAutomatica());
	}

	private void fijarCategoria(CategoriaId nueva, OrigenAsignacion origen) {
		this.categoria = nueva;
		this.origenCategoria = nueva == null ? null : origen;
	}

	private Foto foto() {
		return new Foto(this.categoria, this.origenCategoria, new LinkedHashMap<>(this.etiquetas), this.autoClasificada);
	}

	/** Versiona si algo cambió y emite el evento si cambiaron categoría o etiquetas; devuelve esto último. */
	private boolean anunciarSiCambio(Foto antes, MotivoClasificacion motivo) {
		if (antes.equals(this.foto())) {
			return false;
		}
		boolean clasificacionCambio = !Objects.equals(antes.categoria(), this.categoria)
				|| !List.copyOf(antes.etiquetas().keySet()).equals(List.copyOf(this.etiquetas.keySet()));
		if (clasificacionCambio) {
			this.registrarEvento(new TransaccionClasificada(this.id, this.propietario, this.siguienteVersion(), this.categoria,
					List.copyOf(this.etiquetas.keySet()), motivo));
		} else {
			this.siguienteVersion();
		}
		return clasificacionCambio;
	}

	@Override
	public TransaccionId id() {
		return this.id;
	}

	@Override
	public UsuarioId propietario() {
		return this.propietario;
	}

	public UUID documentoId() {
		return this.documentoId;
	}

	public String concepto() {
		return this.concepto;
	}

	public ClaveConcepto claveConcepto() {
		return this.claveConcepto;
	}

	public long versionOrigen() {
		return this.versionOrigen;
	}

	public CategoriaId categoria() {
		return this.categoria;
	}

	public OrigenAsignacion origenCategoria() {
		return this.origenCategoria;
	}

	/** Etiquetas asignadas, en orden, con su origen. */
	public Map<EtiquetaId, OrigenAsignacion> etiquetas() {
		return java.util.Collections.unmodifiableMap(this.etiquetas);
	}

	public boolean autoClasificada() {
		return this.autoClasificada;
	}
}
