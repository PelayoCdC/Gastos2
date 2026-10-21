# Bitácora

Diario de construcción de Gastos2. Por fase recoge:
- qué se hizo;
- qué se decidió;
- qué problemas aparecieron y cómo se resolvieron;
- en qué se desvió del plan;
- cómo se verificó.

Es la fuente de verdad del **estado** del proyecto. El **porqué** de las decisiones de arquitectura está
en los [ADR](../adr/README.md).

## Estado de las fases

| Fase | Contenido | Estado |
|---|---|---|
| F0 | Fundaciones: monorepo, kernel, soporte de pruebas, compose, documentación base, spikes | ✅ Terminada |
| F1 | identidad + compartido/web y compartido/mensajeria (outbox, relay, inbox) | ✅ Terminada |
| F2 | gateway | ✅ Terminada |
| F3 | movimientos A: cuentas, documentos, transacciones manuales | En curso |
| F4 | movimientos B: registrar extracto, duplicados por conteo | Pendiente |
| F5 | clasificacion A: catálogo, prioridad, siembra, borrados | Pendiente |
| F6 | clasificacion B: motor, automática, propagación, masiva | Pendiente |
| F7 | importacion: formatos, lectura Excel/CSV, importar | Pendiente |
| F8 | consultas A: proyecciones, rejilla, xlsx, leer lo que acabas de escribir | Pendiente |
| F9 | consultas B: informes, análisis, reconstrucción | Pendiente |
| F10 | Respaldo v1 y migración de los datos reales | Pendiente |
| F11 | Observabilidad, e2e y cierre de la documentación | Pendiente |

---

## F0 · Fundaciones (2026-10-21)

### Qué se hizo

- **Repositorio:** git en `Gastos2/`, rama `main`, con `.gitattributes` (LF salvo `.cmd`) y `.gitignore`.
  El Maven Wrapper (Maven 3.9.9) se copió de `gastos-back`.
- **POM raíz** ([ADR 0018](../adr/0018-monorepo-maven-multimodulo.md)):
  - Padre `spring-boot-starter-parent` **4.1.1** e importación de `spring-cloud-dependencies` **2025.1.3**.
  - Versiones de POI 5.5.1, ArchUnit 1.5.1 y springdoc 3.1.1.
  - Enforcer (Java 21, Maven 3.9, convergencia de dependencias) y failsafe para los `*IT`.
  - Las versiones se comprobaron en Maven Central el mismo día. Boot 4.1.1 gestiona Testcontainers 2.0.5,
    Kafka 4.2.1, Jackson 3.1.5 y JUnit 6.0.3.
- **`compartido/kernel`**, en Java puro. El enforcer prohíbe Spring, JPA, Hibernate y Lombok.
  - Dominio: `UsuarioId`, `Importe`, `Normalizador`, `ClaveConcepto`, `RaizAgregado`, `EventoDominio`,
    `ExcepcionDominio`, `RecursoNoEncontrado` y `Conflicto`.
  - Aplicación: `@CasoDeUso`, `UnidadDeTrabajo`, `PublicadorEventos`, `GeneradorIds`, `Pagina` y
    `PeticionPagina`.
  - `ids/Uuid7`.
  - 21 tests unitarios. Los de normalización y clave de concepto están portados de
    `gastos-movil/.../dominio.spec.ts`.
- **`compartido/pruebas`:**
  - `ReglasArquitectura` (ArchUnit): capas, núcleo sin frameworks, núcleo sin reloj implícito, casos de
    uso en aplicación y sin ciclos en el dominio. Su test usa *fixtures* correctas e incorrectas para
    demostrar que las reglas detectan violaciones.
  - `DocumentacionViva`: comprueba los `package-info` de dominio y los términos que faltan en un
    documento.
  - Contenedores `ContenedoresMariaDb` y `ContenedoresKafka` para `@ImportTestcontainers`.
  - Dobles de los puertos comunes.
- **`compose.yaml`:**
  - MariaDB 11.8 con una base y un usuario por servicio (`infra/mariadb/init/01-bases.sql`).
  - Kafka 4.2.2 en KRaft con listeners interno y externo y autocreación desactivada.
  - Puertos 3307 y 9094 para convivir con el stack antiguo.
- **Documentación base:**
  - arc42 (objetivos, contexto C4, plantilla de componentes, conceptos transversales, despliegue,
    riesgos);
  - lenguaje ubicuo, mapa de contextos y un documento por contexto;
  - ADR 0001–0021;
  - comparativa con el back antiguo;
  - guía de desarrollo y operación;
  - esqueletos de escenarios, catálogo de eventos y guía de la API.

### Decisiones y desviaciones del plan

- **Paquetes de capas en español** (`dominio`, `aplicacion`, `infraestructura`) en vez de los nombres en
  inglés que proponía el plan. Así el idioma es coherente en toda la ruta de cada clase
  ([ADR 0021](../adr/0021-nombres-en-espanol.md)).
- **`Uuid7` está en `kernel.ids`**, no en `kernel.aplicacion`. Es la implementación por defecto del
  puerto `GeneradorIds` y usa el reloj del sistema, que la regla ArchUnit prohíbe en las capas de
  aplicación.
- **Versionado de agregados** (`RaizAgregado`): cada cambio con evento incrementa la versión y la estampa
  en su evento. Así varios eventos de la misma operación tienen versiones distintas y los consumidores
  pueden usar una comparación estricta. El plan solo decía "versión por agregado".
- **El `CLUSTER_ID` de Kafka** tiene que ser un UUID en base64 de 22 caracteres. El primero generado
  empezaba por `-`, y el script de arranque lo habría interpretado como una opción, así que se regeneró.

### Spikes

- **UUID nativo de MariaDB** (`SpikeUuidMariaDbIT`): ✅
  - El driver 3.5 y Spring JDBC guardan y consultan UUID sin conversores.
  - `@Version` de Spring Data funciona, pero suma 1 por guardado, y nuestros agregados suman 1 **por
    evento**.
  - **Consecuencia:** la persistencia se hará con `JdbcClient` y SQL explícito
    ([ADR 0022](../adr/0022-persistencia-con-jdbcclient.md), que sustituye al 0007).
  - Primer intento: el repositorio anidado no se detectaba; se arregló con
    `@EnableJdbcRepositories(considerNestedRepositories = true)`.
- **Kafka con Testcontainers 2 sobre Docker 29** (`SpikeKafkaIT`): ✅
  - `apache/kafka:4.2.2` arranca en unos 6 s.
  - Cinco mensajes con la misma clave llegan en orden, que es la base del [ADR 0004](../adr/0004-kafka-topic-por-contexto-clave-usuario.md).

### Problemas encontrados

- **Docker Desktop 4.82 no arrancaba.** Fallaba primero con *initializing Inference manager … remove
  …\Docker\run\dockerInference: El sistema no tiene acceso al archivo*, y después con lo mismo en
  `…\docker-secrets-engine\engine.sock`.
  - **Causa:** sockets AF_UNIX huérfanos (de julio) que el usuario no puede borrar (`ERROR_CANT_ACCESS_FILE`).
    Los sockets que crea un arranque fallido quedan igual de bloqueados.
  - **Solución:** renombrar las carpetas (`run` → `run.huerfano-20260724` / `run.huerfano-20261021`, y
    `docker-secrets-engine` → `docker-secrets-engine.huerfano-20260724`) para que Docker cree unas
    nuevas, y cerrar la instancia que se había quedado en el diálogo de error.
  - Si vuelve a pasar tras un cierre brusco, se repite el renombrado. Seguramente un reinicio de Windows
    también libera los sockets.

### Verificación

- `./mvnw verify` en verde:
  - 21 tests del kernel;
  - 4 tests de reglas de arquitectura;
  - 2 IT de spike con MariaDB y Kafka reales.
- `docker compose up -d mariadb kafka --wait`: los dos *healthy*. MariaDB tiene las 5 bases de datos
  (`identidad`, `movimientos`, `clasificacion`, `importacion`, `consultas`) y sus 5 usuarios.

---

## F1 · identidad y piezas compartidas de web y mensajería (2026-10-21)

### Qué se hizo

- **`compartido/contratos`**, el lenguaje publicado, solo con records del JDK:
  - `Sobre`, `Topics`, `EventoIntegracion` y `CatalogoEventos` (tipo y versión → clase y topic);
  - el primer evento, `identidad.v1.UsuarioRegistrado`.
  - **Tests de contrato:** cada evento tiene una muestra JSON aprobada que el record debe leer y escribir
    igual. Un test de documentación viva exige que cada evento aparezca en `docs/eventos/catalogo.md`.
- **`compartido/web`** (autoconfiguración):
  - resource server JWT con JWKS y validación de emisor y audiencia (`SeguridadAutoConfiguracion`);
  - `@UsuarioActual`;
  - errores RFC 9457 con `codigo` (`ManejadorErrores`);
  - `Clock` (Europe/Madrid) y `GeneradorIds` (UUIDv7) por defecto.
- **`compartido/mensajeria`** (autoconfiguración):
  - `UnidadDeTrabajoTransaccional`;
  - `PublicadorEventosOutbox`, que exige una transacción activa y despierta al relay tras el commit;
  - `RelayOutbox`, con un hilo propio, envío por lotes con `SKIP LOCKED`, clave = usuario y limpieza
    horaria;
  - `Inbox` y `LectorSobres` (lector tolerante);
  - topic propio con retención infinita, DLT por servicio con `DefaultErrorHandler`, y las migraciones
    Flyway compartidas (`db/compartido` V0_1 outbox, V0_2 inbox) que se añaden solas a las de cada
    servicio.
- **`servicios/identidad`:**
  - dominio `Usuario` con los VOs `Email` (canónico), `Nombre`, `ContrasenaEnClaro` y `HashContrasena`;
  - casos de uso `RegistrarUsuario`, `IniciarSesion` y `ConsultarPerfil`;
  - adaptadores JdbcClient, BCrypt y JWT RS256 (`ClaveFirma` lee el PEM PKCS#8, con `kid` = huella
    RFC 7638);
  - JWKS, `TraductorEventosIdentidad` y Flyway V1.
- **Infraestructura:**
  - `Dockerfile` multi-etapa (build común con caché de `.m2`, un target por servicio);
  - servicio `identidad` en `compose.yaml` con healthcheck y puerto solo en localhost;
  - `scripts/generar-claves-dev.sh`;
  - `http/identidad.http`.

### Decisiones y desviaciones

- **`RaizAgregado.registrar(evento)` → `registrarEvento(evento)`.** Chocaba con la factoría de dominio
  `Usuario.registrar(...)`: "registrar" es lenguaje del negocio.
- **DLT por servicio consumidor** (`<servicio>.dlt`) en vez de uno por topic de origen (`<topic>-dlt`,
  como decía el plan). Varios consumidores del mismo topic necesitan DLT independientes, y las cabeceras
  de Spring ya indican el topic original. Actualizado en el catálogo de eventos.
- **El outbox guarda los campos del sobre en columnas** y los `datos` como JSON. El relay compone el
  sobre al enviar, así que la `secuencia` es el id del outbox sin tener que actualizar la fila.
- **401 de credenciales:** `CredencialesInvalidas` es una `ExcepcionDominio` que traduce a 401 un
  `@RestControllerAdvice` propio de identidad con prioridad máxima. El manejador común no conoce el
  concepto de autenticación.
- **Validación de los propios tokens:** identidad los valida con su clave pública en memoria (un
  `JwtDecoder` propio), en vez de pedirse el JWKS a sí misma.

### Verificación

- `./mvnw verify` en verde:
  - unitarios de kernel, contratos, mensajería e identidad;
  - IT: `OutboxIT`, que publica, comprueba el rollback, publicar sin transacción y la idempotencia del
    inbox;
  - IT: `IdentidadApiIT`, que cubre registro, 409, 400, 401, JWKS sin parte privada y el evento en Kafka;
  - ArchUnit y documentación viva.
- **Smoke** con `docker compose up -d --build --wait identidad` y curl contra `localhost:8081`:
  - registro → 201;
  - duplicado → 409 `application/problem+json` con `codigo: EMAIL_YA_REGISTRADO`;
  - el token decodificado lleva `iss`, `aud`, `sub`, `email`, `nombre`, `jti` y `kid`;
  - `/usuarios/yo` → 200, y 401 sin token;
  - `kafka-console-consumer` muestra el sobre de `UsuarioRegistrado` con clave = usuario;
  - `identidad.eventos` tiene 3 particiones y `retention.ms=-1`.

---

## F2 · gateway (2026-10-21)

### Qué se hizo

- **`servicios/gateway`**, con Spring Cloud Gateway Server WebFlux 5.0.3 (tren 2025.1.3):
  - rutas `/api/{identidad|movimientos|clasificacion|importacion|consultas}/**` con `RewritePath` a `/**`;
  - filtro por defecto `RequestSize` de 10 MB;
  - URL de los servicios configurables (`gastos.servicios.*`).
- **`SeguridadGateway`:**
  - `SecurityWebFilterChain` reactiva: `/api/*/internal/**` con `denyAll` (antes que nada), preflight
    `OPTIONS` y registro e inicio de sesión públicos, y el resto con JWT;
  - decodificador con el JWKS de identidad más los validadores de emisor y audiencia;
  - CORS solo para los orígenes configurados (por defecto `http://localhost:4200`), exponiendo
    `Location`, `Content-Disposition`, `Gastos-Secuencia` y `Gastos-Desfase`.
- **`ErroresGateway`:** un servicio inalcanzable (conexión rechazada, host desconocido o timeout de
  conexión) da 503 `problem+json` con `codigo: SERVICIO_NO_DISPONIBLE`.
- **Despliegue:** target `gateway` en el `Dockerfile` y servicio en compose, publicado en el **8090**.

### Decisiones

- **El gateway no depende de `compartido/web` ni del kernel**: `web` es servlet y aquí no hay dominio. Los
  validadores de emisor y audiencia se duplican (unas pocas líneas) en vez de crear un módulo reactivo
  compartido.
- **Las rutas internas se bloquean en la capa de seguridad** (`denyAll` → 403 aunque haya token), no con
  una ruta que no lleve a ninguna parte. Así no dependen del orden de las rutas.
- **Un servicio caído da 503 y no 500** (no estaba en el plan). Lo pedía la coherencia con los errores
  RFC 9457, y le dice al cliente que puede reintentar.

### Verificación

- **`GatewayIT`** (7 tests) contra un servicio simulado con `HttpServer` del JDK, que publica un JWKS
  con una clave RSA generada en el test:
  - con token: enruta quitando el prefijo y conserva `Authorization`;
  - 401 sin token, con otra audiencia o con otro emisor, sin llegar al servicio;
  - registro y sesión públicos;
  - `/internal` → 403 con token y sin llegar al servicio;
  - preflight CORS permitido para el front y 403 para otro origen;
  - 413 con más de 10 MB;
  - 503 `problem+json` si el servicio no responde.
- **Smoke en compose** (`gateway` → `identidad` reales):
  - registro → 201 y sesión con token;
  - `/api/identidad/usuarios/yo` → 200 con token y 401 sin él;
  - `/api/movimientos/internal/extractos` con token → 403;
  - `/api/movimientos/cuentas` (servicio aún sin desplegar) → 503 `SERVICIO_NO_DISPONIBLE`;
  - preflight desde `localhost:4200` → 200 con `Access-Control-Allow-Origin`.
