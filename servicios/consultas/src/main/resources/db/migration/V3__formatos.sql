-- Formatos de banco proyectados desde importacion, solo para la exportación v1 (docs/adr/0020)
CREATE TABLE formato_vista (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	nombre VARCHAR(100) NULL,
	fila_inicio INT NULL,
	columna_concepto INT NULL,
	columna_importe INT NULL,
	columna_fecha_contable INT NULL,
	columna_fecha_valor INT NULL,
	columna_saldo INT NULL,
	formato_fecha VARCHAR(50) NULL,
	eliminado BOOLEAN NOT NULL DEFAULT FALSE,
	version BIGINT NOT NULL DEFAULT 0,
	INDEX ix_formato_vista_usuario (usuario_id)
);
