-- Proyecciones de consultas (docs/dominio/consultas.md, docs/adr/0010). Se alimentan solo de eventos y
-- se pueden reconstruir reproduciendo los topics. Reglas comunes:
--  * Las filas pueden crearse "en esqueleto" (solo id y usuario) por el primer evento que llegue, venga
--    del contexto que venga; por eso casi todas las columnas admiten nulos.
--  * Cada parte de una fila guarda la versión del agregado que la escribió y solo se actualiza con una
--    versión mayor (eventos repetidos o atrasados no hacen nada).
--  * Borrar deja una lápida (eliminada = TRUE): un evento tardío no resucita la fila.

CREATE TABLE cuenta_vista (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	nombre VARCHAR(100) NULL,
	banco VARCHAR(100) NULL,
	descripcion VARCHAR(500) NULL,
	eliminada BOOLEAN NOT NULL DEFAULT FALSE,
	version BIGINT NOT NULL DEFAULT 0,
	INDEX ix_cuenta_vista_usuario (usuario_id)
);

CREATE TABLE documento_vista (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	cuenta_id UUID NULL,
	nombre VARCHAR(255) NULL,
	nombre_archivo VARCHAR(255) NULL,
	fecha DATE NULL,
	eliminado BOOLEAN NOT NULL DEFAULT FALSE,
	version BIGINT NOT NULL DEFAULT 0,
	INDEX ix_documento_vista_usuario (usuario_id)
);

CREATE TABLE transaccion_vista (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	-- Parte de movimientos
	registrada BOOLEAN NOT NULL DEFAULT FALSE,
	eliminada BOOLEAN NOT NULL DEFAULT FALSE,
	version_movimientos BIGINT NOT NULL DEFAULT 0,
	cuenta_id UUID NULL,
	documento_id UUID NULL,
	fecha_contable DATE NULL,
	fecha_valor DATE NULL,
	concepto VARCHAR(500) NULL,
	-- Precalculados al proyectar para buscar sin funciones en el WHERE
	concepto_normalizado VARCHAR(500) COLLATE utf8mb4_bin NULL,
	clave_concepto VARCHAR(500) COLLATE utf8mb4_bin NULL,
	nota VARCHAR(500) NULL,
	importe DECIMAL(15, 2) NULL,
	saldo DECIMAL(15, 2) NULL,
	orden INT NULL,
	orden_documento INT NULL,
	origen VARCHAR(16) NULL,
	-- Parte de clasificacion
	version_clasificacion BIGINT NOT NULL DEFAULT 0,
	categoria_id UUID NULL,
	INDEX ix_transaccion_vista_rejilla (usuario_id, fecha_contable),
	INDEX ix_transaccion_vista_documento (documento_id),
	INDEX ix_transaccion_vista_categoria (categoria_id),
	INDEX ix_transaccion_vista_clave (usuario_id, clave_concepto)
);

CREATE TABLE transaccion_etiqueta_vista (
	transaccion_id UUID NOT NULL,
	etiqueta_id UUID NOT NULL,
	posicion INT NOT NULL,
	PRIMARY KEY (transaccion_id, etiqueta_id),
	INDEX ix_transaccion_etiqueta_vista_etiqueta (etiqueta_id)
);

CREATE TABLE categoria_vista (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	nombre VARCHAR(100) NULL,
	color VARCHAR(7) NULL,
	descripcion VARCHAR(500) NULL,
	-- Lista JSON de textos; solo hace falta para la exportación v1 (F10)
	ocurrencias LONGTEXT NULL,
	prioridad INT NULL,
	eliminada BOOLEAN NOT NULL DEFAULT FALSE,
	version BIGINT NOT NULL DEFAULT 0,
	INDEX ix_categoria_vista_usuario (usuario_id)
);

CREATE TABLE etiqueta_vista (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	nombre VARCHAR(100) NULL,
	descripcion VARCHAR(500) NULL,
	ocurrencias LONGTEXT NULL,
	categoria_id UUID NULL,
	prioridad INT NULL,
	eliminada BOOLEAN NOT NULL DEFAULT FALSE,
	version BIGINT NOT NULL DEFAULT 0,
	INDEX ix_etiqueta_vista_usuario (usuario_id)
);

-- Hasta qué secuencia de cada contexto se han proyectado los eventos de cada usuario (docs/adr/0011)
CREATE TABLE proyeccion_posicion (
	usuario_id UUID NOT NULL,
	contexto VARCHAR(30) NOT NULL,
	secuencia BIGINT NOT NULL,
	PRIMARY KEY (usuario_id, contexto)
);
