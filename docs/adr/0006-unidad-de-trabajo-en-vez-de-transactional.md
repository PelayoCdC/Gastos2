# ADR 0006: Capas Clean, núcleo sin frameworks y `UnidadDeTrabajo` en vez de `@Transactional`

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

En el back antiguo la estructura hexagonal era nominal:
- las entidades eran de JPA;
- los repositorios extendían Spring Data;
- 6 servicios de aplicación importaban clases de infraestructura;
- los criteria extendían clases de Spring Data.

La regla de dependencia de Clean Architecture (*el código solo depende hacia dentro*) no se cumplía y
nada lo comprobaba.

## Opciones consideradas

1. **Hexagonal pragmática.** Se acepta Spring en todas partes y `@Transactional` en los servicios.
2. **Núcleo puro.**
   - `dominio` y `aplicacion` sin dependencias de framework.
   - Los casos de uso son clases simples registradas desde la configuración.
   - La transacción se expresa con un puerto propio.
   - Una herramienta verifica todo lo anterior.

## Decisión

Opción 2, por servicio:

```
es.gastos.<servicio>
  dominio/          agregados, VOs, eventos de dominio, servicios de dominio, puertos de repositorio
  aplicacion/       un @CasoDeUso por clase (puerto de entrada), comandos/consultas, puertos de salida
  infraestructura/  adaptadores de entrada (REST, Kafka) y de salida (persistencia, outbox, HTTP), config
```

- **`@CasoDeUso`** es una anotación propia del kernel. La configuración la registra con un filtro de
  escaneo.
- **Transacciones:** el puerto `UnidadDeTrabajo.ejecutar(() -> ...)` delimita la transacción dentro del
  caso de uso. Su adaptador usa `TransactionTemplate`, y en los tests unitarios se sustituye por una
  ejecución directa.
- **Fecha actual:** se obtiene de un `java.time.Clock` inyectado.
- **La propiedad de los datos** ("si no es tuyo, 404") se comprueba en el caso de uso, no en el
  controlador.
- **ArchUnit** (`ReglasArquitectura`, en `compartido/pruebas`) hace fallar el build si:
  - se rompe la regla de capas;
  - entra Spring, JPA, Jackson, Kafka, POI o Lombok en `dominio` o `aplicacion`;
  - se llama a `LocalDate.now()` o similares en el núcleo;
  - hay ciclos entre los módulos de dominio.

## Consecuencias

- ✅ Dominio y aplicación se prueban con tests unitarios rápidos y sin Spring.
- ✅ La frontera transaccional es visible en el código del caso de uso.
- ✅ La arquitectura deja de ser una convención: es verificable.
- ⚠️ Hay algo más de código: SQL y mapeo entre dominio y persistencia ([0022](0022-persistencia-con-jdbcclient.md)),
  configuración de beans y DTOs de entrada y salida.
- ⚠️ Los desarrolladores acostumbrados a Spring echarán de menos `@Transactional` y `@Service`; la guía de
  desarrollo explica el patrón.
