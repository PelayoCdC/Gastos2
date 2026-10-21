-- Esquema del contexto movimientos (docs/dominio/movimientos.md). Solo hechos bancarios: la
-- clasificación (categoría, etiquetas) vive en el servicio clasificacion.

CREATE TABLE cuenta (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	nombre VARCHAR(100) NOT NULL,
	-- Unicidad por usuario sobre la forma normalizada (docs/adr/0013); collation binaria a propósito
	nombre_normalizado VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
	banco VARCHAR(100) NULL,
	descripcion VARCHAR(500) NULL,
	version BIGINT NOT NULL,
	CONSTRAINT uk_cuenta_nombre UNIQUE (usuario_id, nombre_normalizado)
);

CREATE TABLE documento (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	-- Nula solo en documentos migrados del back antiguo
	cuenta_id UUID NULL,
	nombre VARCHAR(255) NOT NULL,
	nombre_archivo VARCHAR(255) NOT NULL,
	fecha DATE NOT NULL,
	-- Respuesta de la importación, para devolver lo mismo si se reintenta con el mismo extractoId
	resultado_registro LONGTEXT NULL,
	version BIGINT NOT NULL,
	INDEX ix_documento_usuario (usuario_id, fecha),
	INDEX ix_documento_cuenta (cuenta_id),
	CONSTRAINT fk_documento_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta (id)
);

CREATE TABLE transaccion (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	cuenta_id UUID NULL,
	documento_id UUID NULL,
	fecha_contable DATE NOT NULL,
	fecha_valor DATE NULL,
	concepto VARCHAR(500) NOT NULL,
	nota VARCHAR(500) NULL,
	importe DECIMAL(15, 2) NOT NULL,
	-- Nulo si el extracto no trae saldo o si la transacción es manual (antes valía 0 y falseaba el saldo)
	saldo DECIMAL(15, 2) NULL,
	orden INT NOT NULL,
	orden_documento INT NOT NULL,
	origen VARCHAR(16) NOT NULL,
	-- SHA-256 de fecha|importe|concepto (docs/adr/0015)
	clave_duplicado CHAR(64) NOT NULL,
	version BIGINT NOT NULL,
	INDEX ix_transaccion_duplicados (usuario_id, cuenta_id, clave_duplicado),
	INDEX ix_transaccion_saldo (cuenta_id, fecha_contable, orden),
	INDEX ix_transaccion_documento (documento_id),
	CONSTRAINT fk_transaccion_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
	CONSTRAINT fk_transaccion_documento FOREIGN KEY (documento_id) REFERENCES documento (id)
);
