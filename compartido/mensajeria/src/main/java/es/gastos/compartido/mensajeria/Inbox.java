package es.gastos.compartido.mensajeria;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Inbox para consumidores con efectos: garantiza que cada evento se procesa una sola vez por
 * consumidor aunque Kafka lo entregue varias veces (docs/adr/0005).
 *
 * <p>Debe usarse dentro de la misma transacción que el efecto: si el efecto falla, el registro del
 * inbox también se deshace y el evento se reintentará.
 */
public class Inbox {

	private final JdbcClient jdbc;

	private final Clock reloj;

	public Inbox(JdbcClient jdbc, Clock reloj) {
		this.jdbc = jdbc;
		this.reloj = reloj;
	}

	/** Ejecuta el efecto si este consumidor no había procesado ya el evento; devuelve si lo ejecutó. */
	public boolean procesarUnaVez(UUID eventoId, String consumidor, Runnable efecto) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("El inbox se usa dentro de la transaccion del efecto");
		}

		int registrados = this.jdbc.sql("INSERT IGNORE INTO inbox (evento_id, consumidor, procesado_en) VALUES (:id, :consumidor, :ahora)")
				.param("id", eventoId)
				.param("consumidor", consumidor)
				.param("ahora", LocalDateTime.ofInstant(this.reloj.instant(), ZoneOffset.UTC))
				.update();

		if (registrados == 0) {
			return false;
		}

		efecto.run();
		return true;
	}
}
