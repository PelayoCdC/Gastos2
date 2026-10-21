package es.gastos.compartido.mensajeria;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Acceso SQL a la tabla {@code outbox}. Las fechas se guardan en UTC. */
public class OutboxRepositorio {

	/** Evento pendiente de enviar, tal y como está en la tabla. */
	public record FilaOutbox(long id, UUID eventoId, String topic, String tipo, int version, UUID usuarioId,
			UUID agregadoId, long versionAgregado, Instant ocurridoEn, String datos, String traceparent) {
	}

	private final JdbcClient jdbc;

	public OutboxRepositorio(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Inserta el evento y devuelve su id, que es la secuencia del contexto. */
	public long insertar(FilaOutbox fila) {
		KeyHolder clave = new GeneratedKeyHolder();
		this.jdbc.sql("""
						INSERT INTO outbox (evento_id, topic, tipo, version, usuario_id, agregado_id, version_agregado,
						                    ocurrido_en, datos, traceparent)
						VALUES (:eventoId, :topic, :tipo, :version, :usuarioId, :agregadoId, :versionAgregado,
						        :ocurridoEn, :datos, :traceparent)
						""")
				.param("eventoId", fila.eventoId())
				.param("topic", fila.topic())
				.param("tipo", fila.tipo())
				.param("version", fila.version())
				.param("usuarioId", fila.usuarioId())
				.param("agregadoId", fila.agregadoId())
				.param("versionAgregado", fila.versionAgregado())
				.param("ocurridoEn", utc(fila.ocurridoEn()))
				.param("datos", fila.datos())
				.param("traceparent", fila.traceparent())
				.update(clave, "id");
		return clave.getKeyAs(Number.class).longValue();
	}

	/**
	 * Pendientes en orden de inserción, bloqueados para este relay ({@code SKIP LOCKED}: si hubiera
	 * otra instancia, se saltaría las filas que ya está enviando otra). Debe llamarse en transacción.
	 */
	public List<FilaOutbox> bloquearPendientes(int lote) {
		return this.jdbc.sql("""
						SELECT id, evento_id, topic, tipo, version, usuario_id, agregado_id, version_agregado,
						       ocurrido_en, datos, traceparent
						FROM outbox
						WHERE publicado_en IS NULL
						ORDER BY id
						LIMIT :lote
						FOR UPDATE SKIP LOCKED
						""")
				.param("lote", lote)
				.query((rs, n) -> new FilaOutbox(
						rs.getLong("id"),
						rs.getObject("evento_id", UUID.class),
						rs.getString("topic"),
						rs.getString("tipo"),
						rs.getInt("version"),
						rs.getObject("usuario_id", UUID.class),
						rs.getObject("agregado_id", UUID.class),
						rs.getLong("version_agregado"),
						rs.getObject("ocurrido_en", LocalDateTime.class).toInstant(ZoneOffset.UTC),
						rs.getString("datos"),
						rs.getString("traceparent")))
				.list();
	}

	public void marcarPublicados(List<Long> ids, Instant instante) {
		if (ids.isEmpty()) {
			return;
		}
		this.jdbc.sql("UPDATE outbox SET publicado_en = :instante WHERE id IN (:ids)")
				.param("instante", utc(instante))
				.param("ids", ids)
				.update();
	}

	public int borrarPublicadosAntesDe(Instant limite) {
		return this.jdbc.sql("DELETE FROM outbox WHERE publicado_en IS NOT NULL AND publicado_en < :limite")
				.param("limite", utc(limite))
				.update();
	}

	public long pendientes() {
		return this.jdbc.sql("SELECT COUNT(*) FROM outbox WHERE publicado_en IS NULL").query(Long.class).single();
	}

	private static LocalDateTime utc(Instant instante) {
		return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
	}
}
