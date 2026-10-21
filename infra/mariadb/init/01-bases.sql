-- Una base de datos y un usuario por servicio (database-per-service): cada servicio solo puede
-- leer y escribir sus propias tablas. Nadie consulta la base de datos de otro servicio; los datos
-- ajenos llegan por eventos o por API. Ver docs/adr/0001.
--
-- Este script solo lo ejecuta la imagen de MariaDB al crear el volumen por primera vez.
-- Las contraseñas son de desarrollo; en otro entorno se inyectan como secretos.

CREATE DATABASE IF NOT EXISTS identidad CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
CREATE DATABASE IF NOT EXISTS movimientos CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
CREATE DATABASE IF NOT EXISTS clasificacion CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
CREATE DATABASE IF NOT EXISTS importacion CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
CREATE DATABASE IF NOT EXISTS consultas CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;

CREATE USER IF NOT EXISTS 'identidad'@'%' IDENTIFIED BY 'identidad-dev';
CREATE USER IF NOT EXISTS 'movimientos'@'%' IDENTIFIED BY 'movimientos-dev';
CREATE USER IF NOT EXISTS 'clasificacion'@'%' IDENTIFIED BY 'clasificacion-dev';
CREATE USER IF NOT EXISTS 'importacion'@'%' IDENTIFIED BY 'importacion-dev';
CREATE USER IF NOT EXISTS 'consultas'@'%' IDENTIFIED BY 'consultas-dev';

GRANT ALL PRIVILEGES ON identidad.* TO 'identidad'@'%';
GRANT ALL PRIVILEGES ON movimientos.* TO 'movimientos'@'%';
GRANT ALL PRIVILEGES ON clasificacion.* TO 'clasificacion'@'%';
GRANT ALL PRIVILEGES ON importacion.* TO 'importacion'@'%';
GRANT ALL PRIVILEGES ON consultas.* TO 'consultas'@'%';

FLUSH PRIVILEGES;
