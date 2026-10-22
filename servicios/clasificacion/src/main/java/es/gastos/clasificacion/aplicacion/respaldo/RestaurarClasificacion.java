package es.gastos.clasificacion.aplicacion.respaldo;

import es.gastos.clasificacion.aplicacion.siembra.RegistroSiembra;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionRepositorio;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.respaldo.ClasificacionRestaurada;
import es.gastos.clasificacion.dominio.respaldo.PlanRestauracion;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sustituye el catálogo y las clasificaciones del usuario por los de un respaldo (docs/adr/0020), o solo
 * informa de lo que haría ({@code simular}). Es la parte de clasificacion de la restauración que
 * orquesta importacion.
 *
 * <p>En una transacción: se olvidan las clasificaciones y se borra el catálogo del usuario, se marca el
 * catálogo como sembrado (para que la siembra inicial no se aplique encima de lo restaurado), se publica
 * {@code ClasificacionRestaurada} y después el alta de cada categoría, etiqueta y clasificación.
 */
@CasoDeUso
public class RestaurarClasificacion {

	public record Comando(UsuarioId usuario, List<PlanRestauracion.CategoriaRespaldo> categorias,
			List<PlanRestauracion.EtiquetaRespaldo> etiquetas, List<PlanRestauracion.TransaccionRespaldo> transacciones, boolean simular) {
	}

	public record Resumen(int categorias, int etiquetas, int clasificadas, List<String> anomalias) {
	}

	private final CatalogoRepositorio catalogo;

	private final ClasificacionRepositorio clasificaciones;

	private final RegistroSiembra siembra;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public RestaurarClasificacion(CatalogoRepositorio catalogo, ClasificacionRepositorio clasificaciones, RegistroSiembra siembra,
			PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo) {
		this.catalogo = catalogo;
		this.clasificaciones = clasificaciones;
		this.siembra = siembra;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public Resumen ejecutar(Comando comando) {
		PlanRestauracion.Plan plan = PlanRestauracion.de(comando.usuario(), lista(comando.categorias()), lista(comando.etiquetas()),
				lista(comando.transacciones()));
		Resumen resumen = new Resumen(plan.categorias().size(), plan.etiquetas().size(), plan.clasificadas(), plan.anomalias());

		this.unidadDeTrabajo.ejecutar(() -> {
			UsuarioId usuario = comando.usuario();
			this.exigirIdsLibres(usuario, plan);
			if (comando.simular()) {
				return;
			}
			this.clasificaciones.olvidarDelUsuario(usuario);
			this.catalogo.eliminarDelUsuario(usuario);
			this.siembra.marcar(usuario);
			this.publicador.publicar(List.of(new ClasificacionRestaurada(usuario, resumen.categorias(), resumen.etiquetas(),
					resumen.clasificadas())));

			for (Categoria categoria : plan.categorias()) {
				this.catalogo.guardar(categoria);
				this.publicador.publicarDe(categoria);
			}
			for (Etiqueta etiqueta : plan.etiquetas()) {
				this.catalogo.guardar(etiqueta);
				this.publicador.publicarDe(etiqueta);
			}
			for (ClasificacionTransaccion clasificacion : plan.clasificaciones()) {
				this.clasificaciones.guardar(clasificacion);
				this.publicador.publicarDe(clasificacion);
			}
		});
		return resumen;
	}

	/** Los ids se conservan (docs/adr/0020): no pueden ser ya de otra cuenta. Se comprueba antes de borrar nada. */
	private void exigirIdsLibres(UsuarioId usuario, PlanRestauracion.Plan plan) {
		List<UUID> catalogo = new ArrayList<>();
		plan.categorias().forEach(c -> catalogo.add(c.id().valor()));
		plan.etiquetas().forEach(e -> catalogo.add(e.id().valor()));
		if (this.catalogo.algunoDeOtroUsuario(usuario, catalogo) || this.clasificaciones.algunaDeOtroUsuario(usuario,
				plan.clasificaciones().stream().map(c -> c.id().valor()).toList())) {
			throw new Conflicto("RESPALDO_DE_OTRA_CUENTA",
					"El respaldo contiene datos de otra cuenta de usuario: solo se puede restaurar en la cuenta de la que salio");
		}
	}

	private static <T> List<T> lista(List<T> lista) {
		return lista == null ? List.of() : lista;
	}
}
