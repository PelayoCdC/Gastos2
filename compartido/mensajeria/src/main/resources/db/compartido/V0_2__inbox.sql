-- Inbox (docs/adr/0005): los consumidores con efectos registran aquí cada evento procesado, en la
-- misma transacción que su efecto, para ignorar las entregas repetidas de Kafka.
CREATE TABLE inbox (
	evento_id UUID NOT NULL,
	consumidor VARCHAR(100) NOT NULL,
	procesado_en DATETIME(6) NOT NULL,
	PRIMARY KEY (evento_id, consumidor)
);
