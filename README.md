# Gastos2

Back de **Gastos** (control de gastos personal: importar extractos bancarios, clasificar los
movimientos con categorías y etiquetas, y ver informes) reescrito desde cero como
**microservicios**, aplicando **DDD** (estratégico y táctico), **Clean Architecture** y
**arquitectura hexagonal** (puertos y adaptadores).

Sustituye al monolito `Gastos/gastos-back`. Además de cambiar la arquitectura, corrige sus defectos
conocidos (dinero en `float`, nombres repetidos, orden de clasificación arbitrario, borrados que
fallaban…). Todo lo que cambia está en [docs/comparativa-con-gastos-back.md](docs/comparativa-con-gastos-back.md).

## Arrancarlo

Requisitos: Java 21 y Docker Desktop. Maven no hace falta, se usa el wrapper.

```bash
docker compose up -d mariadb kafka --wait   # infraestructura
./mvnw verify                               # compila y pasa todos los tests (unitarios, IT con Testcontainers, arquitectura)
docker compose up -d --build --wait         # todo el stack (a medida que se añaden los servicios)
```

## Estructura

| Carpeta | Contenido |
|---|---|
| `compartido/kernel` | Kernel compartido: `UsuarioId`, `Importe`, normalización, clave de concepto, base de agregados y puertos comunes |
| `compartido/pruebas` | Soporte de tests: reglas ArchUnit, contenedores (MariaDB, Kafka), dobles |
| `servicios/*` | Un microservicio por contexto acotado: identidad, movimientos, clasificacion, importacion, consultas, gateway |
| `docs/` | Documentación: arquitectura (arc42 + C4), dominio, decisiones (ADR), eventos, API y proceso |
| `infra/` | Scripts de inicialización de MariaDB y claves de desarrollo |

## Documentación

Empieza por [docs/README.md](docs/README.md). Las decisiones de arquitectura y su porqué están en
[docs/adr/](docs/adr/), y el diario de construcción en [docs/proceso/bitacora.md](docs/proceso/bitacora.md).
