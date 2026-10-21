package es.gastos.contratos;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre común de todos los eventos publicados en Kafka.
 *
 * @param eventoId        id único del evento (idempotencia de los consumidores con inbox)
 * @param tipo            nombre del evento, p. ej. {@code UsuarioRegistrado}
 * @param contexto        servicio productor
 * @param version         versión del contrato del evento
 * @param secuencia       id del outbox del productor: crece con cada evento del contexto; se usa para
 *                        "leer lo que acabas de escribir" (docs/adr/0011)
 * @param ocurridoEn      instante en que se registró el evento
 * @param usuarioId       propietario de los datos (también es la clave del mensaje)
 * @param agregadoId      agregado que cambió
 * @param versionAgregado versión del agregado tras el cambio; los consumidores descartan las antiguas
 * @param datos           contenido específico del evento
 */
public record Sobre<T>(
		UUID eventoId,
		String tipo,
		String contexto,
		int version,
		long secuencia,
		Instant ocurridoEn,
		UUID usuarioId,
		UUID agregadoId,
		long versionAgregado,
		T datos) {

	public <R> Sobre<R> conDatos(R otrosDatos) {
		return new Sobre<>(this.eventoId, this.tipo, this.contexto, this.version, this.secuencia, this.ocurridoEn,
				this.usuarioId, this.agregadoId, this.versionAgregado, otrosDatos);
	}
}
