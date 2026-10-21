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

## Pendiente de completar

| Tarea | Fase |
|---|---|
| Reconstruir `consultas` | F9 |
| Restaurar un respaldo y migrar desde el back antiguo | F10 |
| Revisar los DLT y reprocesar eventos | F11 |
| Observabilidad (trazas, métricas, logs) | F11 |
