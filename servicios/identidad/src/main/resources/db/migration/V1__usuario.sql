-- Usuarios registrados. El email se guarda ya en forma canónica (minúsculas, sin espacios) y con
-- collation binaria: la unicidad la define el dominio (Email), no la collation (docs/adr/0013).
CREATE TABLE usuario (
	id UUID NOT NULL PRIMARY KEY,
	email VARCHAR(254) COLLATE utf8mb4_bin NOT NULL,
	nombre VARCHAR(100) NOT NULL,
	hash_contrasena VARCHAR(100) NOT NULL,
	registrado_en DATETIME(6) NOT NULL,
	version BIGINT NOT NULL,
	CONSTRAINT uk_usuario_email UNIQUE (email)
);
