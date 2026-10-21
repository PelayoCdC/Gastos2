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
| F1 | identidad + compartido/web y compartido/mensajeria (outbox, relay, inbox) | En curso |
| F2 | gateway | Pendiente |
| F3 | movimientos A: cuentas, documentos, transacciones manuales | Pendiente |
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
