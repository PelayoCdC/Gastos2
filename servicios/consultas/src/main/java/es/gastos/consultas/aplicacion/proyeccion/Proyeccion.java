package es.gastos.consultas.aplicacion.proyeccion;

import es.gastos.kernel.dominio.UsuarioId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida: las tablas de lectura de consultas. Cada operación es <b>idempotente y protegida
 * por versión</b> (docs/adr/0010): si llega un evento repetido o más antiguo que lo ya proyectado, no
 * cambia nada. Así se puede reprocesar un lote, o el topic entero, sin estropear el estado.
 *
 * <p>Un mismo registro puede recibir eventos de dos contextos (la transacción: hechos de movimientos y
 * clasificación de clasificacion), que llegan por topics distintos sin orden entre sí. Por eso cada
 * parte lleva su propia versión y la fila se crea con el primero que llegue ("fila parcial").
 *
 * <p>Las eliminaciones dejan una lápida en vez de borrar la fila: un evento tardío del otro contexto
 * (por ejemplo, la clasificación de una transacción ya borrada) no la resucita.
 */
public interface Proyeccion {

	void guardarCuenta(UsuarioId usuario, Cuenta cuenta, long version);

	void eliminarCuenta(UsuarioId usuario, UUID cuentaId, long version);

	void registrarDocumento(UsuarioId usuario, Documento documento, long version);

	void renombrarDocumento(UsuarioId usuario, UUID documentoId, String nombre, long version);

	/** Evento de conjunto: también pasa a la cuenta nueva todas las transacciones del documento. */
	void reasignarDocumento(UsuarioId usuario, UUID documentoId, UUID cuentaId, long version);

	/** Evento de conjunto: también deja lápida en todas las transacciones del documento. */
	void eliminarDocumento(UsuarioId usuario, UUID documentoId, long version);

	void registrarTransaccion(UsuarioId usuario, Hechos hechos, Registro registro, long version);

	void corregirTransaccion(UsuarioId usuario, Hechos hechos, long version);

	void eliminarTransaccion(UsuarioId usuario, UUID transaccionId, long version);

	void clasificarTransaccion(UsuarioId usuario, UUID transaccionId, UUID categoriaId, List<UUID> etiquetaIds, long version);

	void guardarCategoria(UsuarioId usuario, Categoria categoria, long version);

	/** Evento de conjunto: las transacciones y etiquetas que la tenían se quedan sin categoría. */
	void eliminarCategoria(UsuarioId usuario, UUID categoriaId, long version);

	void guardarEtiqueta(UsuarioId usuario, Etiqueta etiqueta, long version);

	/** Evento de conjunto: se quita de todas las transacciones que la tenían. */
	void eliminarEtiqueta(UsuarioId usuario, UUID etiquetaId, long version);

	record Cuenta(UUID id, String nombre, String banco, String descripcion) {
	}

	record Documento(UUID id, UUID cuentaId, String nombre, String nombreArchivo, LocalDate fecha) {
	}

	/** Lo que cualquier evento de transacción trae completo; el concepto ya va normalizado para buscar. */
	record Hechos(UUID id, UUID cuentaId, LocalDate fechaContable, LocalDate fechaValor, String concepto, String conceptoNormalizado,
			String claveConcepto, String nota, BigDecimal importe) {
	}

	/** Lo que solo se conoce al registrar la transacción y no cambia después. */
	record Registro(UUID documentoId, BigDecimal saldo, int orden, int ordenDocumento, String origen) {
	}

	record Categoria(UUID id, String nombre, String color, String descripcion, List<String> ocurrencias, int prioridad) {
	}

	record Etiqueta(UUID id, String nombre, String descripcion, List<String> ocurrencias, UUID categoriaId, int prioridad) {
	}
}
