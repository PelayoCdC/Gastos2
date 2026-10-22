package es.gastos.consultas.aplicacion.proyeccion;

import es.gastos.consultas.aplicacion.consistencia.PosicionesLectura;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.contratos.Sobre;
import es.gastos.contratos.clasificacion.v1.CategoriaEliminada;
import es.gastos.contratos.clasificacion.v1.CategoriaGuardada;
import es.gastos.contratos.clasificacion.v1.ClasificacionRestaurada;
import es.gastos.contratos.clasificacion.v1.EtiquetaEliminada;
import es.gastos.contratos.clasificacion.v1.EtiquetaGuardada;
import es.gastos.contratos.clasificacion.v1.TransaccionClasificada;
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
import es.gastos.kernel.aplicacion.CasoDeUso;
import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import es.gastos.kernel.dominio.ClaveConcepto;
import es.gastos.kernel.dominio.Normalizador;
import es.gastos.kernel.dominio.UsuarioId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aplica un lote de eventos publicados a las proyecciones, en una sola transacción, y avanza la
 * posición de lectura de cada usuario y contexto.
 *
 * <p>consultas es <b>conformista</b> (docs/dominio/mapa-de-contextos.md): su modelo es el lenguaje
 * publicado por los demás, por eso trabaja directamente con los contratos. Lo único que añade es lo que
 * necesita para buscar rápido: el concepto normalizado y su clave sin fechas, calculados una vez al
 * proyectar con las mismas funciones del kernel que usa clasificacion.
 */
@CasoDeUso
public class ProyectarEventos {

	private final Proyeccion proyeccion;

	private final PosicionesLectura posiciones;

	private final UnidadDeTrabajo unidadDeTrabajo;

	public ProyectarEventos(Proyeccion proyeccion, PosicionesLectura posiciones, UnidadDeTrabajo unidadDeTrabajo) {
		this.proyeccion = proyeccion;
		this.posiciones = posiciones;
		this.unidadDeTrabajo = unidadDeTrabajo;
	}

	public void aplicar(List<Sobre<EventoIntegracion>> sobres) {
		if (sobres.isEmpty()) {
			return;
		}
		this.unidadDeTrabajo.ejecutar(() -> {
			Map<UsuarioId, Map<String, Long>> alcanzadas = new LinkedHashMap<>();
			for (Sobre<EventoIntegracion> sobre : sobres) {
				UsuarioId usuario = new UsuarioId(sobre.usuarioId());
				this.proyectar(usuario, sobre.datos(), sobre.versionAgregado());
				alcanzadas.computeIfAbsent(usuario, u -> new LinkedHashMap<>()).merge(sobre.contexto(), sobre.secuencia(), Math::max);
			}
			alcanzadas.forEach((usuario, porContexto) -> porContexto.forEach(
					(contexto, secuencia) -> this.posiciones.avanzar(usuario, contexto, secuencia)));
		});
	}

	private void proyectar(UsuarioId usuario, EventoIntegracion evento, long version) {
		switch (evento) {
			case CuentaGuardada e -> this.proyeccion.guardarCuenta(usuario,
					new Proyeccion.Cuenta(e.cuentaId(), e.nombre(), e.banco(), e.descripcion()), version);
			case CuentaEliminada e -> this.proyeccion.eliminarCuenta(usuario, e.cuentaId(), version);
			case DocumentoRegistrado e -> this.proyeccion.registrarDocumento(usuario,
					new Proyeccion.Documento(e.documentoId(), e.cuentaId(), e.nombre(), e.nombreArchivo(), e.fecha()), version);
			case DocumentoRenombrado e -> this.proyeccion.renombrarDocumento(usuario, e.documentoId(), e.nombre(), version);
			case DocumentoReasignado e -> this.proyeccion.reasignarDocumento(usuario, e.documentoId(), e.cuentaIdNueva(), version);
			case DocumentoEliminado e -> this.proyeccion.eliminarDocumento(usuario, e.documentoId(), version);
			case TransaccionRegistrada e -> this.proyeccion.registrarTransaccion(usuario,
					hechos(e.transaccionId(), e.cuentaId(), e.fechaContable(), e.fechaValor(), e.concepto(), e.nota(), e.importe()),
					new Proyeccion.Registro(e.documentoId(), e.saldo(), e.orden(), e.ordenDocumento(), e.origen()), version);
			case TransaccionCorregida e -> this.proyeccion.corregirTransaccion(usuario,
					hechos(e.transaccionId(), e.cuentaId(), e.fechaContable(), e.fechaValor(), e.concepto(), e.nota(), e.importe()), version);
			case TransaccionEliminada e -> this.proyeccion.eliminarTransaccion(usuario, e.transaccionId(), version);
			case TransaccionClasificada e -> this.proyeccion.clasificarTransaccion(usuario, e.transaccionId(), e.categoriaId(),
					e.etiquetaIds() == null ? List.of() : e.etiquetaIds(), version);
			case CategoriaGuardada e -> this.proyeccion.guardarCategoria(usuario, new Proyeccion.Categoria(e.categoriaId(), e.nombre(),
					e.color(), e.descripcion(), lista(e.ocurrencias()), e.prioridad()), version);
			case CategoriaEliminada e -> this.proyeccion.eliminarCategoria(usuario, e.categoriaId(), version);
			case EtiquetaGuardada e -> this.proyeccion.guardarEtiqueta(usuario, new Proyeccion.Etiqueta(e.etiquetaId(), e.nombre(),
					e.descripcion(), lista(e.ocurrencias()), e.categoriaId(), e.prioridad()), version);
			case EtiquetaEliminada e -> this.proyeccion.eliminarEtiqueta(usuario, e.etiquetaId(), version);
			case FormatoBancoGuardado e -> this.proyeccion.guardarFormato(usuario, new Proyeccion.Formato(e.formatoId(), e.nombre(),
					e.filaInicio(), e.columnaConcepto(), e.columnaImporte(), e.columnaFechaContable(), e.columnaFechaValor(), e.columnaSaldo(),
					e.formatoFecha()), version);
			case FormatoBancoEliminado e -> this.proyeccion.eliminarFormato(usuario, e.formatoId(), version);
			case MovimientosRestaurados e -> this.proyeccion.restaurarMovimientos(usuario);
			case ClasificacionRestaurada e -> this.proyeccion.restaurarClasificacion(usuario);
			case FormatosRestaurados e -> this.proyeccion.restaurarFormatos(usuario);
			default -> {
				// Eventos que consultas no proyecta (aún): se cuentan para la posición y nada más
			}
		}
	}

	private static Proyeccion.Hechos hechos(UUID id, UUID cuentaId, LocalDate fechaContable, LocalDate fechaValor, String concepto,
			String nota, BigDecimal importe) {
		String texto = concepto == null ? "" : concepto;
		return new Proyeccion.Hechos(id, cuentaId, fechaContable, fechaValor, texto, Normalizador.normalizar(texto),
				ClaveConcepto.de(texto).valor(), nota, importe);
	}

	private static List<String> lista(List<String> ocurrencias) {
		return ocurrencias == null ? List.of() : ocurrencias;
	}
}
