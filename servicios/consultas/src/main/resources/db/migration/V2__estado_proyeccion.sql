-- Versión de la proyección que hay en las tablas (docs/adr/0010). Si al arrancar no coincide con
-- gastos.consultas.proyeccion.version, consultas vacía las tablas y relee los topics desde el principio.
CREATE TABLE proyeccion_estado (
	id TINYINT NOT NULL PRIMARY KEY,
	version INT NOT NULL,
	actualizada_en DATETIME(6) NOT NULL,
	CONSTRAINT ck_proyeccion_estado_unica CHECK (id = 1)
);
