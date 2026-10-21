# 6. Despliegue

Todo el sistema se despliega en local con un único `compose.yaml` en la raíz del repositorio.

## Contenedores y puertos

| Servicio compose | Imagen | Puerto en el host | Notas |
|---|---|---|---|
| `mariadb` | `mariadb:11.8` | 3307 | Una base y un usuario por servicio (`infra/mariadb/init/01-bases.sql`); volumen `datos-mariadb` |
| `kafka` | `apache/kafka:4.2.2` | 9094 | KRaft, un nodo, autocreación de topics desactivada; volumen `datos-kafka` |
| servicios y gateway | se construyen desde el `Dockerfile` raíz | gateway 8090 (el resto, solo en el perfil dev) | Se añaden en F1–F9 |

Los puertos del host se eligieron para convivir con el stack antiguo (`Gastos/docker-compose.yaml`),
que usa 8080, 3306 y 4200.

## Listeners de Kafka

| Listener | Dirección | Uso |
|---|---|---|
| `INTERNO` | `kafka:9092` | Contenedores dentro de la red de compose |
| `EXTERNO` | `localhost:9094` | Procesos en el host (IDE, herramientas) |
| `CONTROLLER` | `kafka:9093` | Quórum KRaft |

## Volúmenes y respaldo

- `datos-mariadb`: el estado de todos los servicios.
- `datos-kafka`: el log de eventos, con retención infinita. **Hay que respaldarlo:** es la fuente para
  reconstruir `consultas` ([ADR 0010](../adr/0010-consultas-cqrs-reconstruible-por-replay.md)).

## Recursos

El objetivo es que todo quepa en unos 3 GB: cada JVM con `mem_limit` de unos 512 MB y
`-XX:MaxRAMPercentage=75`. Se ajusta en F11 midiendo.
