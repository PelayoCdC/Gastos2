-- Esquema del contexto clasificacion (docs/dominio/clasificacion.md).

CREATE TABLE categoria (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	nombre VARCHAR(100) NOT NULL,
	nombre_normalizado VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
	color CHAR(7) NULL,
	descripcion VARCHAR(500) NULL,
	-- Menor = antes: decide qué categoría gana cuando varias coinciden (docs/adr/0014)
	prioridad INT NOT NULL,
	version BIGINT NOT NULL,
	CONSTRAINT uk_categoria_nombre UNIQUE (usuario_id, nombre_normalizado),
	INDEX ix_categoria_prioridad (usuario_id, prioridad)
);

-- Las ocurrencias conservan sus espacios ("bar "): collation binaria y nada de recortes
CREATE TABLE categoria_ocurrencia (
	categoria_id UUID NOT NULL,
	posicion INT NOT NULL,
	texto VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
	PRIMARY KEY (categoria_id, posicion),
	CONSTRAINT fk_categoria_ocurrencia FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE CASCADE
);

CREATE TABLE etiqueta (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	nombre VARCHAR(100) NOT NULL,
	nombre_normalizado VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
	descripcion VARCHAR(500) NULL,
	-- Categoría que aplica en cascada; el caso de uso la desasigna al borrar la categoría
	categoria_id UUID NULL,
	prioridad INT NOT NULL,
	version BIGINT NOT NULL,
	CONSTRAINT uk_etiqueta_nombre UNIQUE (usuario_id, nombre_normalizado),
	INDEX ix_etiqueta_prioridad (usuario_id, prioridad),
	CONSTRAINT fk_etiqueta_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE SET NULL
);

CREATE TABLE etiqueta_ocurrencia (
	etiqueta_id UUID NOT NULL,
	posicion INT NOT NULL,
	texto VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
	PRIMARY KEY (etiqueta_id, posicion),
	CONSTRAINT fk_etiqueta_ocurrencia FOREIGN KEY (etiqueta_id) REFERENCES etiqueta (id) ON DELETE CASCADE
);

-- Clasificación de cada transacción: copia mínima de la transacción (alimentada por los eventos de
-- movimientos) + asignación con el origen de cada parte (docs/adr/0002, docs/adr/0017)
CREATE TABLE clasificacion_transaccion (
	id UUID NOT NULL PRIMARY KEY,
	usuario_id UUID NOT NULL,
	documento_id UUID NULL,
	concepto VARCHAR(500) NOT NULL,
	clave_concepto VARCHAR(500) COLLATE utf8mb4_bin NOT NULL,
	-- Versión de la transacción en movimientos que refleja esta copia (descarta eventos antiguos)
	version_origen BIGINT NOT NULL,
	categoria_id UUID NULL,
	origen_categoria VARCHAR(16) NULL,
	auto_clasificada BOOLEAN NOT NULL,
	version BIGINT NOT NULL,
	INDEX ix_clasificacion_clave (usuario_id, clave_concepto),
	INDEX ix_clasificacion_documento (documento_id),
	INDEX ix_clasificacion_categoria (categoria_id),
	CONSTRAINT fk_clasificacion_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE SET NULL
);

CREATE TABLE clasificacion_etiqueta (
	transaccion_id UUID NOT NULL,
	etiqueta_id UUID NOT NULL,
	posicion INT NOT NULL,
	origen VARCHAR(16) NOT NULL,
	PRIMARY KEY (transaccion_id, etiqueta_id),
	INDEX ix_clasificacion_etiqueta (etiqueta_id),
	CONSTRAINT fk_clasificacion_etiqueta_tx FOREIGN KEY (transaccion_id) REFERENCES clasificacion_transaccion (id) ON DELETE CASCADE,
	CONSTRAINT fk_clasificacion_etiqueta_et FOREIGN KEY (etiqueta_id) REFERENCES etiqueta (id) ON DELETE CASCADE
);

-- Marca de "catálogo inicial ya sembrado" (una vez por usuario, aunque luego borre las entradas)
CREATE TABLE catalogo_sembrado (
	usuario_id UUID NOT NULL PRIMARY KEY,
	sembrado_en DATETIME(6) NOT NULL
);
