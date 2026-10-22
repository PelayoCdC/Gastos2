# CLAUDE.md

Guía para trabajar en este repositorio (Claude Code y personas).

## Qué es

Reescritura del back de Gastos (`F:\IntelliJIDEA Workspace\Gastos\gastos-back`, monolito) como
microservicios con DDD, Clean Architecture y Hexagonal. Monorepo Maven multi-módulo con un único
`compose.yaml`. Todo el código, el dominio y la documentación están en **español**: mantén ese
idioma en clases, paquetes, endpoints y documentos.

La referencia funcional es el back antiguo y la app móvil `Gastos/gastos-movil` (lógica de dominio en
`src/app/core/dominio/` con tests vitest que sirven como tests de aceptación). Las diferencias
intencionadas están en `docs/comparativa-con-gastos-back.md`.

## Comandos

```bash
docker compose up -d mariadb kafka --wait     # MariaDB (host 3307) y Kafka (host 9094)
./mvnw verify                                 # todo: unit (*Test) + integración (*IT, Testcontainers) + ArchUnit
./mvnw -pl compartido/kernel test             # un módulo
./mvnw -pl servicios/<svc> -am verify         # un servicio y lo que necesita
./mvnw -pl <modulo> test -Dtest=Clase#metodo  # un test
```

- `JAVA_HOME` debe apuntar a un JDK 21 (en esta máquina: `C:\Program Files\Java\jdk-21.0.11`).
- Docker Desktop no arranca solo en esta máquina; los `*IT` lo necesitan (Testcontainers).
- El stack antiguo usa 8080/3306/4200: Gastos2 usa otros puertos para poder convivir.

## Arquitectura en una página

- **Servicios** (`servicios/`), uno por contexto acotado: `identidad`, `movimientos`, `clasificacion`
  (core domain), `importacion` (capa anticorrupción de formatos de banco), `consultas` (lado de
  lectura CQRS) y `gateway`. Cada uno tiene su base de datos y nadie lee la de otro.
- **Capas por servicio** (paquete `es.gastos.<servicio>`):
  - `dominio`: Java puro con agregados, VOs, eventos de dominio, servicios de dominio y puertos de repositorio.
  - `aplicacion`: una clase `@CasoDeUso` por caso de uso, comandos y consultas como records, puertos de salida y comprobación de propiedad.
  - `infraestructura`: REST, Kafka, persistencia con modelo propio y mapeadores, adaptadores y configuración.

  ArchUnit (`compartido/pruebas` → `ReglasArquitectura`) hace fallar el build si se rompe la regla de
  dependencia, si entra un framework en `dominio`/`aplicacion` o si se usa `LocalDate.now()` en vez del
  `Clock` inyectado.
- **Transacciones**: puerto `UnidadDeTrabajo` (no `@Transactional` en aplicación).
- **Eventos**: el agregado registra eventos de dominio y el caso de uso los publica con
  `PublicadorEventos` dentro de la unidad de trabajo. Eso escribe en el **outbox** y un relay los
  envía a Kafka. Hay un topic por contexto (`<ctx>.eventos`) con clave = `usuarioId`. Los
  consumidores con efectos usan **inbox** (idempotencia); las proyecciones usan versiones.
- **Leer lo que acabas de escribir**: las respuestas de los comandos llevan `Gastos-Secuencia` sin
  hacer nada (lo pone un filtro de `compartido/mensajeria` con lo que escribió el outbox); consultas
  atiende `Gastos-Esperar`. Si un servicio provoca escrituras en otro, reenvía su secuencia con
  `SecuenciaPublicada.registrarCabecera(...)` (ver `RegistroMovimientosHttp`).
- **Llamadas entre servicios**: síncronas solo por `/internal/**`, reenviando el JWT del usuario con
  `UsuarioAutenticado.cabeceraAutorizacion()` (sin cuentas de servicio) y con `@Retryable` ante fallos
  de red.
- **Multiusuario**: todo pertenece a un usuario; un recurso ajeno es **404**, nunca 403. El usuario sale
  del JWT (`sub`), nunca del cliente.
- **Dinero**: `Importe` (BigDecimal, escala 2). Los nombres son únicos por usuario sobre
  `Normalizador.normalizarNombre`.

## Convenciones

- Sin Lombok. Los VOs y los comandos son `record`. Los agregados extienden `RaizAgregado`: cada cambio
  hace `siguienteVersion()` y `registrarEvento(evento)`.
- Javadoc en el dominio explicando invariantes y el **porqué** de cada regla no obvia; cada paquete de
  `dominio` tiene su `package-info.java` (lo comprueba un test).
- Tests: `*Test` son unitarios (sin Docker) y `*IT` son de integración (Testcontainers:
  `@ImportTestcontainers(ContenedoresMariaDb.class)`). En los unitarios de aplicación se usan los dobles
  de `compartido/pruebas` (`UnidadDeTrabajoDirecta`, `PublicadorEventosEnMemoria`,
  `GeneradorIdsSecuencial`, `Relojes`).

## Documentación (obligatoria en cada cambio)

En `docs/`. Al terminar un trabajo:
1. Añade una entrada en `docs/proceso/bitacora.md`.
2. Crea o actualiza el ADR en `docs/adr/` si has tomado o cambiado una decisión. Los superados se marcan, no se borran.
3. Actualiza el doc del contexto en `docs/dominio/`, `docs/eventos/catalogo.md` (un test comprueba que
   están todos los eventos) y los escenarios o diagramas afectados.

El plan completo por fases y su estado están en la bitácora.
