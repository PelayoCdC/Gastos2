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

## Pendiente de completar

| Tarea | Fase |
|---|---|
| Generar las claves RSA de desarrollo | F1 |
| Reconstruir `consultas` | F9 |
| Restaurar un respaldo y migrar desde el back antiguo | F10 |
| Revisar los DLT y reprocesar eventos | F11 |
| Observabilidad (trazas, métricas, logs) | F11 |
