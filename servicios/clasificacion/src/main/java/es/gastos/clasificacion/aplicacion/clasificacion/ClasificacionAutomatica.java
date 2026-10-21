package es.gastos.clasificacion.aplicacion.clasificacion;

import es.gastos.clasificacion.aplicacion.siembra.SembrarCatalogoInicial;
import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionRepositorio;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.clasificacion.ModoAutomatico;
import es.gastos.clasificacion.dominio.clasificacion.MotorClasificacion;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;

/**
 * Pasa las reglas del catálogo por todas las transacciones del usuario: tras cambiar ocurrencias o
 * prioridades, para que el histórico refleje las reglas nuevas. REINICIAR respeta lo manual salvo
 * que se fuerce (docs/adr/0017). Las transacciones cuya clasificación no cambia no emiten evento.
 */
@CasoDeUso
public class ClasificacionAutomatica {

	public record Resultado(int procesadas, int etiquetadas, int categorizadas) {
	}

	private final ClasificacionRepositorio clasificaciones;

	private final CatalogoRepositorio catalogo;

	private final SembrarCatalogoInicial siembra;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public ClasificacionAutomatica(ClasificacionRepositorio clasificaciones, CatalogoRepositorio catalogo,
			SembrarCatalogoInicial siembra, PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo) {
		this.clasificaciones = clasificaciones;
		this.catalogo = catalogo;
		this.siembra = siembra;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public Resultado ejecutar(UsuarioId usuario, ModoAutomatico modo, boolean forzar) {
		if (modo == ModoAutomatico.IMPORTACION) {
			throw new ExcepcionDominio("MODO_INVALIDO", "El modo IMPORTACION solo lo usa la importacion de extractos");
		}

		return this.unidadDeTrabajo.ejecutar(() -> {
			this.siembra.asegurar(usuario);
			MotorClasificacion motor = MotorClasificacion.de(this.catalogo.categorias(usuario), this.catalogo.etiquetas(usuario));
			int procesadas = 0;
			int etiquetadas = 0;
			int categorizadas = 0;
			for (ClasificacionTransaccion clasificacion : this.clasificaciones.delUsuario(usuario)) {
				ClasificacionTransaccion.Resultado resultado = clasificacion.clasificarAutomaticamente(motor, modo, forzar);
				procesadas++;
				etiquetadas += resultado.etiquetada() ? 1 : 0;
				categorizadas += resultado.categorizada() ? 1 : 0;
				this.clasificaciones.guardar(clasificacion);
				this.publicador.publicarDe(clasificacion);
			}
			return new Resultado(procesadas, etiquetadas, categorizadas);
		});
	}
}
