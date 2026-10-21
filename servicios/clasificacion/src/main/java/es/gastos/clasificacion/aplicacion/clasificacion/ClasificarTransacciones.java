package es.gastos.clasificacion.aplicacion.clasificacion;

import es.gastos.clasificacion.aplicacion.siembra.SembrarCatalogoInicial;
import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionRepositorio;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.GeneradorIds;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Casos de uso de clasificación decididos por el usuario sobre transacciones concretas: asignar a
 * mano, ver las similares y propagarles la clasificación, y aplicar en bloque a una selección.
 * Todos admiten transacciones que este contexto aún no conoce (read-through a movimientos) y
 * categorías o etiquetas nuevas por nombre.
 */
@CasoDeUso
public class ClasificarTransacciones {

	/** Clasificación pedida: ids existentes y/o nombres de entradas nuevas del catálogo. */
	public record Peticion(UUID categoriaId, String nuevaCategoria, List<UUID> etiquetaIds, List<String> nuevasEtiquetas) {
	}

	public record Similares(int similares, String claveConcepto) {
	}

	public record ResultadoBloque(int actualizadas, int ignoradas) {
	}

	private final ClasificacionRepositorio clasificaciones;

	private final CatalogoRepositorio catalogo;

	private final SembrarCatalogoInicial siembra;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	private final TransaccionesConocidas conocidas;

	private final ResolucionCatalogo resolucion;

	public ClasificarTransacciones(ClasificacionRepositorio clasificaciones, CatalogoRepositorio catalogo,
			OrigenTransacciones origen, SembrarCatalogoInicial siembra, PublicadorEventos publicador,
			UnidadDeTrabajo unidadDeTrabajo, GeneradorIds ids) {
		this.clasificaciones = clasificaciones;
		this.catalogo = catalogo;
		this.siembra = siembra;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
		this.conocidas = new TransaccionesConocidas(clasificaciones, origen);
		this.resolucion = new ResolucionCatalogo(catalogo, publicador, ids);
	}

	public DatosClasificacion consultar(UsuarioId usuario, TransaccionId id) {
		return this.unidadDeTrabajo.ejecutar(() -> this.vista(usuario, this.conocidas.obtener(usuario, id)));
	}

	/** Fija la clasificación completa de la transacción (sustituye categoría y etiquetas). */
	public DatosClasificacion asignar(UsuarioId usuario, TransaccionId id, Peticion peticion) {
		return this.unidadDeTrabajo.ejecutar(() -> {
			this.siembra.asegurar(usuario);
			ClasificacionTransaccion clasificacion = this.conocidas.obtener(usuario, id);
			CategoriaId categoria = this.resolucion.categoria(usuario, peticion.categoriaId(), peticion.nuevaCategoria());
			List<EtiquetaId> etiquetas = this.resolucion.etiquetas(usuario, peticion.etiquetaIds(), peticion.nuevasEtiquetas());

			clasificacion.asignarManualmente(categoria, etiquetas);
			this.clasificaciones.guardar(clasificacion);
			this.publicador.publicarDe(clasificacion);
			return this.vista(usuario, clasificacion);
		});
	}

	/** Cuántas transacciones del usuario tienen la misma clave de concepto (sin fechas, sin tildes). */
	public Similares similares(UsuarioId usuario, TransaccionId id) {
		return this.unidadDeTrabajo.ejecutar(() -> {
			ClasificacionTransaccion base = this.conocidas.obtener(usuario, id);
			if (base.claveConcepto().esVacia()) {
				return new Similares(0, "");
			}
			return new Similares(this.clasificaciones.similares(usuario, base.claveConcepto(), id).size(), base.claveConcepto().valor());
		});
	}

	/** Copia la clasificación de la transacción a sus similares; devuelve cuántas cambiaron. */
	public int propagar(UsuarioId usuario, TransaccionId id) {
		return this.unidadDeTrabajo.ejecutar(() -> {
			ClasificacionTransaccion base = this.conocidas.obtener(usuario, id);
			if (base.claveConcepto().esVacia()) {
				return 0;
			}
			int cambiadas = 0;
			for (ClasificacionTransaccion similar : this.clasificaciones.similares(usuario, base.claveConcepto(), id)) {
				if (similar.propagarDesde(base)) {
					cambiadas++;
				}
				this.clasificaciones.guardar(similar);
				this.publicador.publicarDe(similar);
			}
			return cambiadas;
		});
	}

	/**
	 * Aplica categoría y etiquetas a una selección. Las transacciones ajenas o inexistentes se ignoran
	 * (y se cuentan) en vez de fallar: la selección viene de una rejilla que puede estar desfasada.
	 */
	public ResultadoBloque aplicarEnBloque(UsuarioId usuario, List<UUID> transacciones, Peticion peticion, boolean sustituirEtiquetas) {
		return this.unidadDeTrabajo.ejecutar(() -> {
			this.siembra.asegurar(usuario);
			CategoriaId categoria = this.resolucion.categoria(usuario, peticion.categoriaId(), peticion.nuevaCategoria());
			List<EtiquetaId> etiquetas = this.resolucion.etiquetas(usuario, peticion.etiquetaIds(), peticion.nuevasEtiquetas());

			List<TransaccionId> ids = transacciones.stream().distinct().map(TransaccionId::new).toList();
			List<ClasificacionTransaccion> encontradas = this.conocidas.obtenerVarias(usuario, ids);
			int actualizadas = 0;
			for (ClasificacionTransaccion clasificacion : encontradas) {
				if (clasificacion.aplicarEnBloque(categoria, etiquetas, sustituirEtiquetas)) {
					actualizadas++;
				}
				this.clasificaciones.guardar(clasificacion);
				this.publicador.publicarDe(clasificacion);
			}
			return new ResultadoBloque(actualizadas, ids.size() - encontradas.size());
		});
	}

	private DatosClasificacion vista(UsuarioId usuario, ClasificacionTransaccion clasificacion) {
		Map<CategoriaId, Categoria> categorias = this.catalogo.categorias(usuario).stream()
				.collect(Collectors.toMap(Categoria::id, Function.identity()));
		Map<EtiquetaId, Etiqueta> etiquetas = this.catalogo.etiquetas(usuario).stream()
				.collect(Collectors.toMap(Etiqueta::id, Function.identity()));
		return DatosClasificacion.de(clasificacion, categorias, etiquetas);
	}
}
