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
# Eventos de un topic (suma de las posiciones finales de sus particiones)
docker compose exec kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 \
  --topic movimientos.eventos
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

## Reconstruir consultas

Las tablas de consultas se pueden tirar y rehacer desde Kafka en cualquier momento (cambio de esquema
de lectura, proyección estropeada, o simplemente para comprobar que se puede):

```bash
scripts/reconstruir-consultas.sh        # versión actual + 1
scripts/reconstruir-consultas.sh 7      # una versión concreta
```

El script guarda `GASTOS_CONSULTAS_PROYECCION_VERSION` en `.env` (para que los siguientes
`docker compose up` usen la misma versión), recrea el contenedor de consultas y espera a que el retraso
de la proyección sea 0. Al arrancar con una versión distinta de la guardada, consultas vacía sus tablas
y relee los topics desde el principio.

- Progreso: `curl http://127.0.0.1:8085/actuator/health/proyeccion` (`retraso` = eventos pendientes).
- Durante la reconstrucción las consultas devuelven lo que lleve proyectado.
- **Requisito:** el volumen `datos-kafka` tiene que conservar todo el historial (retención infinita). Si
  se pierde, consultas no se puede reconstruir; el plan B (`republicar-estado`) llega en F11.

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
| Restaurar un respaldo y migrar desde el back antiguo | F10 |
| Revisar los DLT y reprocesar eventos | F11 |
| Observabilidad (trazas, métricas, logs) | F11 |
