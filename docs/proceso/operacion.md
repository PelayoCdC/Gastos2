# Operación

Tareas habituales para tener el sistema en marcha. Cada sección se completa en la fase que introduce la
tarea.

## Arrancar y parar

```bash
docker compose up -d mariadb kafka --wait   # infraestructura
docker compose up -d --build --wait         # todo el stack
docker compose ps
docker compose logs -f <servicio>
docker compose down                         # conserva los datos
docker compose down -v                      # BORRA los datos (MariaDB y el log de Kafka)
```

## Inspeccionar Kafka

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic identidad.eventos --from-beginning --property print.key=true
```

En Git Bash, las rutas que empiezan por `/` se convierten a rutas de Windows. Antepón
`MSYS_NO_PATHCONV=1` a los `docker compose exec` que lleven rutas del contenedor.

## Claves de firma de los JWT

```bash
scripts/generar-claves-dev.sh            # crea infra/claves/jwt-privada.pem si no existe
scripts/generar-claves-dev.sh --forzar   # la regenera: los tokens emitidos dejan de valer
```

- La clave privada solo la monta `identidad` (en `/claves`, de solo lectura), y no se versiona.
- **Sin clave**, identidad arranca con una efímera y avisa en el log, porque
  `GASTOS_IDENTIDAD_PERMITIR_CLAVE_EFIMERA=true` en desarrollo. Con una clave efímera, los tokens dejan
  de valer al reiniciar.
- El JWKS público está en `GET /.well-known/jwks.json`. El `kid` es la huella de la clave, así que no
  cambia mientras la clave no cambie.

## Problemas conocidos del entorno (Windows + Docker Desktop)

- **Docker Desktop no arranca tras reiniciar Windows.** El backend falla con "initializing Inference
  manager ... remove ...\Docker\run\dockerInference: El sistema no tiene acceso al archivo": son sockets
  de la sesión anterior que no se pueden borrar. Se arregla cerrando Docker Desktop, renombrando
  `%LOCALAPPDATA%\Docker\run` y `%LOCALAPPDATA%\docker-secrets-engine` (por ejemplo a
  `run.huerfano-<fecha>`) y volviéndolo a abrir: crea directorios nuevos.
- **Falta de memoria con todo levantado.** Sin archivo de paginación, el VM de WSL de Docker retiene
  la caché de disco aunque los contenedores estén parados, y las JVM (o incluso `bash`) fallan con
  "insufficient memory". Se libera sin parar nada con
  `wsl -d docker-desktop -u root sh -c "sync; echo 3 > /proc/sys/vm/drop_caches"`. Para el
  `./mvnw verify` completo conviene parar antes los servicios de compose.

## Pendiente de completar

| Tarea | Fase |
|---|---|
| Reconstruir `consultas` | F9 |
| Restaurar un respaldo y migrar desde el back antiguo | F10 |
| Revisar los DLT y reprocesar eventos | F11 |
| Observabilidad (trazas, métricas, logs) | F11 |
