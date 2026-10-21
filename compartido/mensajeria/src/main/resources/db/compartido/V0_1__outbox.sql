-- Outbox transaccional (docs/adr/0005): los eventos se escriben aquí en la misma transacción que el
-- cambio de negocio y un relay los envía a Kafka después del commit. El id es la "secuencia" del
-- contexto, que crece con cada evento (docs/adr/0011).
CREATE TABLE outbox (
	id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
	evento_id UUID NOT NULL,
	topic VARCHAR(200) NOT NULL,
	tipo VARCHAR(100) NOT NULL,
	version INT NOT NULL,
	usuario_id UUID NOT NULL,
	agregado_id UUID NOT NULL,
	version_agregado BIGINT NOT NULL,
	ocurrido_en DATETIME(6) NOT NULL,
	datos LONGTEXT NOT NULL,
	traceparent VARCHAR(100) NULL,
	publicado_en DATETIME(6) NULL,
	CONSTRAINT uk_outbox_evento UNIQUE (evento_id),
	INDEX ix_outbox_pendientes (publicado_en, id)
);
