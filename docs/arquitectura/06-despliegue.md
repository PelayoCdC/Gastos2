# 6. Despliegue

Todo el sistema se despliega en local con un único `compose.yaml` en la raíz del repositorio.

## Contenedores y puertos

| Servicio compose | Imagen | Puerto en el host | Notas |
|---|---|---|---|
| `mariadb` | `mariadb:11.8` | 3307 | Una base y un usuario por servicio (`infra/mariadb/init/01-bases.sql`); volumen `datos-mariadb` |
| `kafka` | `apache/kafka:4.2.2` | 9094 | KRaft, un nodo, autocreación de topics desactivada; volumen `datos-kafka` |
| `gateway` | `Dockerfile` raíz, target `gateway` | **8090** (la única entrada) | Valida JWT con el JWKS de `identidad:8080`; CORS para `localhost:4200` |
| `identidad` | `Dockerfile` raíz, target `identidad` | `127.0.0.1:8081` (solo para depurar) | Monta `infra/claves` de solo lectura; healthcheck `/actuator/health` |
| resto de servicios | `Dockerfile` raíz, un target por servicio | `127.0.0.1:8082`–`8085` | Se añaden en F3–F9 |

**Imágenes.** El `Dockerfile` raíz tiene una etapa `build` que compila todo el monorepo una vez, con
caché de `~/.m2` gracias a BuildKit, y una etapa final por servicio sobre `eclipse-temurin:21-jre`, que
ya trae `curl` para los healthchecks. Dentro del contenedor todos los servicios escuchan en el 8080.

**Puertos de los servicios.** Se publican solo en `127.0.0.1` para poder depurar sin pasar por el
gateway. Así no quedan expuestos a la red, y sus rutas `/internal/**` tampoco.

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
