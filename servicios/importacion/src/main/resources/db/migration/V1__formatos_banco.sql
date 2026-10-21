-- Esquema del contexto importacion (docs/dominio/importacion.md). Solo los formatos de banco: los
-- ficheros no se guardan (se interpretan en memoria y las líneas viajan a movimientos).

CREATE TABLE formato_banco (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	nombre VARCHAR(100) NOT NULL,
	-- Unicidad por usuario sobre la forma normalizada (docs/adr/0013); collation binaria a propósito
	nombre_normalizado VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
	-- Fila (desde 1) en la que empiezan los movimientos y columnas (desde 0) de cada campo
	fila_inicio INT NOT NULL,
	columna_concepto INT NOT NULL,
	columna_importe INT NOT NULL,
	columna_fecha_contable INT NOT NULL,
	columna_fecha_valor INT NULL,
	columna_saldo INT NULL,
	formato_fecha VARCHAR(50) NOT NULL,
	version BIGINT NOT NULL,
	CONSTRAINT uk_formato_nombre UNIQUE (usuario_id, nombre_normalizado)
);
