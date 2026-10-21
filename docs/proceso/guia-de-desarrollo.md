# Guía de desarrollo

## Requisitos

- **JDK 21.** En la máquina de desarrollo está en `C:\Program Files\Java\jdk-21.0.11`, y `JAVA_HOME`
  debe apuntar a él.
- **Docker Desktop**, para los tests de integración (Testcontainers) y el stack. No arranca solo:
  ábrelo antes y espera a que el engine responda (`docker info`).
- **Maven** no hace falta: usa `./mvnw` (Git Bash) o `.\mvnw.cmd` (PowerShell).

## Comandos habituales

```bash
docker compose up -d mariadb kafka --wait     # infraestructura (MariaDB en el host 3307, Kafka en 9094)
./mvnw verify                                 # todo: unitarios + integración + arquitectura + documentación viva
./mvnw test                                   # solo unitarios (rápido, sin Docker)
./mvnw -pl servicios/movimientos -am verify   # un servicio y sus dependencias
./mvnw -pl compartido/kernel test -Dtest=ImporteTest#normalizaSiempreADosDecimales
```

## Tipos de test

| Sufijo | Plugin | Qué prueba | Necesita Docker |
|---|---|---|---|
| `*Test` | surefire | Dominio y aplicación con dobles; reglas de arquitectura; documentación viva | No |
| `*IT` | failsafe | Adaptadores contra MariaDB y Kafka reales (Testcontainers), API con MockMvc | Sí |
| e2e (`pruebas-e2e`, perfil `-Pe2e`) | failsafe | Escenarios completos contra el stack de `compose.yaml` | Sí (stack levantado) |

**Dobles de los puertos comunes** (en `compartido/pruebas`):
- `UnidadDeTrabajoDirecta`;
- `PublicadorEventosEnMemoria` (con `deTipo` y `unico` para comprobar eventos);
- `GeneradorIdsSecuencial`;
- `Relojes.fijo("2026-10-21")`.

**Contenedores:** `@ImportTestcontainers(ContenedoresMariaDb.class)` y lo mismo con
`ContenedoresKafka`. Se arrancan una sola vez por JVM.

## Estructura de un servicio

```
servicios/<svc>/src/main/java/es/gastos/<svc>/
  dominio/<agregado>/        Agregado (extends RaizAgregado), VOs (records), eventos (records), puerto XRepositorio
                             + package-info.java explicando el agregado (obligatorio, lo comprueba un test)
  aplicacion/                Un caso de uso por clase (@CasoDeUso) + records de comando/consulta/resultado
  infraestructura/rest/      Controladores y DTOs de entrada/salida
  infraestructura/persistencia/  Adaptadores de los puertos de repositorio con JdbcClient (SQL explícito + RowMapper)
  infraestructura/eventos/   Traductor de eventos de dominio → contratos de integración
  infraestructura/kafka/     Listeners (consumidores)
  infraestructura/config/    Wiring: beans de casos de uso, Clock, GeneradorIds...
servicios/<svc>/src/main/resources/db/migration/   Flyway V1__..., V2__...
```

## Receta: añadir un caso de uso

1. **Dominio:**
   - Si la operación tiene reglas, implementa un método en el agregado que valide las invariantes,
     haga `siguienteVersion()` y `registrarEvento(new EventoX(..., version))`.
   - Escribe su test unitario.
2. **Aplicación:**
   - Crea `class HacerX` con `@CasoDeUso`, que recibe sus puertos por constructor.
   - Su método `ejecutar(comando)`:
     1. abre `unidadDeTrabajo.ejecutar(() -> { ... })`;
     2. carga el agregado con su repositorio, comprobando la propiedad: si no es del usuario, lanza
        `RecursoNoEncontrado`;
     3. invoca el método del dominio;
     4. guarda;
     5. llama a `publicador.publicarDe(agregado)`.
   - Escribe su test unitario con dobles.
3. **Infraestructura:**
   - Endpoint en el controlador: el usuario sale de `@UsuarioActual UsuarioId`, nunca del cuerpo.
   - Si hay evento nuevo: su contrato en `compartido/contratos`, su traducción y su entrada en
     `docs/eventos/catalogo.md` (un test lo exige).
4. **Documentación:**
   - Doc del contexto en `docs/dominio/`.
   - Escenario en `docs/arquitectura/04-escenarios.md` si es relevante.
   - Entrada en la bitácora.
   - ADR si has tomado una decisión.

## Convenciones

- Todo en español, y los identificadores sin tildes ni eñes ([ADR 0021](../adr/0021-nombres-en-espanol.md)).
- Sin Lombok. Los VOs, comandos y DTOs son `record`.
- **Javadoc:** en el dominio, explicando invariantes y el **porqué** de las reglas no obvias. Fuera del
  dominio, comenta solo lo que no se ve leyendo el código.
- Los errores de negocio se lanzan como `ExcepcionDominio`, `RecursoNoEncontrado` o `Conflicto` con un
  `codigo` en MAYÚSCULAS. La capa web los convierte en ProblemDetail.
- Nunca se usa `LocalDate.now()` ni similares en `dominio`/`aplicacion`: se inyecta un `Clock`.
- Los commits se hacen por fase o por unidad coherente, con el mensaje en español.
