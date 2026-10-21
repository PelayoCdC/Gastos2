package es.gastos.clasificacion.aplicacion.sincronizacion;

import es.gastos.clasificacion.aplicacion.siembra.SembrarCatalogoInicial;
import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionRepositorio;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.clasificacion.ModoAutomatico;
import es.gastos.clasificacion.dominio.clasificacion.MotorClasificacion;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.UUID;

/**
 * Mantiene la copia local de las transacciones con los eventos de movimientos y lanza la
 * clasificación automática de las importadas (una sola vez por transacción). Las altas manuales y las
 * migradas no se clasifican solas, como en el back antiguo: el usuario decide.
 *
 * <p>Es idempotente sin inbox: los eventos repetidos o antiguos los descarta la versión de origen y la
 * clasificación automática solo ocurre si la transacción no estaba ya auto-clasificada.
 */
@CasoDeUso
public class SincronizarTransacciones {

	public static final String ORIGEN_IMPORTACION = "IMPORTACION";

	private final ClasificacionRepositorio clasificaciones;

	private final CatalogoRepositorio catalogo;

	private final SembrarCatalogoInicial siembra;

	private final PublicadorEventos publicador;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public SincronizarTransacciones(ClasificacionRepositorio clasificaciones, CatalogoRepositorio catalogo,
			SembrarCatalogoInicial siembra, PublicadorEventos publicador, UnidadDeTrabajo unidadDeTrabajo) {
		this.clasificaciones = clasificaciones;
		this.catalogo = catalogo;
		this.siembra = siembra;
		this.publicador = publicador;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public void registrada(UsuarioId usuario, TransaccionId id, UUID documentoId, String concepto, String origen, long versionOrigen) {
		this.unidadDeTrabajo.ejecutar(() -> {
			ClasificacionTransaccion clasificacion = this.conocidaOSincronizada(usuario, id, documentoId, concepto, versionOrigen);
			if (clasificacion == null) {
				return;
			}

			if (ORIGEN_IMPORTACION.equals(origen) && !clasificacion.autoClasificada()) {
				this.siembra.asegurar(usuario);
				MotorClasificacion motor = MotorClasificacion.de(this.catalogo.categorias(usuario), this.catalogo.etiquetas(usuario));
				clasificacion.clasificarAutomaticamente(motor, ModoAutomatico.IMPORTACION, false);
			}
			this.clasificaciones.guardar(clasificacion);
			this.publicador.publicarDe(clasificacion);
		});
	}

	/** Los hechos corregidos no reclasifican (tampoco lo hacía el back antiguo): solo se actualiza la copia. */
	public void corregida(UsuarioId usuario, TransaccionId id, String concepto, long versionOrigen) {
		this.unidadDeTrabajo.ejecutar(() -> {
			ClasificacionTransaccion existente = this.clasificaciones.buscar(id).orElse(null);
			UUID documento = existente == null ? null : existente.documentoId();
			ClasificacionTransaccion clasificacion = this.conocidaOSincronizada(usuario, id, documento, concepto, versionOrigen);
			if (clasificacion != null) {
				this.clasificaciones.guardar(clasificacion);
			}
		});
	}

	public void eliminada(TransaccionId id) {
		this.unidadDeTrabajo.ejecutar(() -> this.clasificaciones.olvidar(id));
	}

	public void documentoEliminado(UUID documentoId) {
		this.unidadDeTrabajo.ejecutar(() -> this.clasificaciones.olvidarDocumento(documentoId));
	}

	/** Devuelve la clasificación actualizada, o nula si el id pertenece a otro usuario (no debería ocurrir). */
	private ClasificacionTransaccion conocidaOSincronizada(UsuarioId usuario, TransaccionId id, UUID documentoId, String concepto,
			long versionOrigen) {
		ClasificacionTransaccion clasificacion = this.clasificaciones.buscar(id).orElse(null);
		if (clasificacion == null) {
			return ClasificacionTransaccion.conocer(id, usuario, documentoId, concepto, versionOrigen);
		}
		if (!clasificacion.perteneceA(usuario)) {
			return null;
		}
		clasificacion.sincronizarOrigen(concepto, documentoId, versionOrigen);
		return clasificacion;
	}
}
