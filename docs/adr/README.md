# Registro de decisiones de arquitectura (ADR)

Cada ADR recoge una decisión relevante con:
- su **contexto**: el problema y las fuerzas en juego;
- las **opciones consideradas**;
- la **decisión**;
- sus **consecuencias**, buenas y malas.

Un ADR no se borra ni se reescribe cuando la decisión cambia: se marca como *sustituida por* el nuevo,
para conservar la historia del porqué. Plantilla: [0000-plantilla.md](0000-plantilla.md).

| Nº | Decisión | Estado |
|---|---|---|
| [0001](0001-microservicios-por-contexto-acotado.md) | Un microservicio por contexto acotado, con su propia base de datos | Aceptada |
| [0002](0002-asignaciones-en-clasificacion.md) | La clasificación de cada transacción vive en `clasificacion`, no en `movimientos` | Aceptada |
| [0003](0003-importacion-como-capa-anticorrupcion.md) | `importacion` es un servicio aparte y actúa de capa anticorrupción | Aceptada |
| [0004](0004-kafka-topic-por-contexto-clave-usuario.md) | Kafka con un topic por contexto y el usuario como clave | Aceptada |
| [0005](0005-outbox-e-inbox-propios.md) | Outbox transaccional e inbox implementados a mano | Aceptada |
| [0006](0006-unidad-de-trabajo-en-vez-de-transactional.md) | Capas Clean, núcleo sin frameworks y `UnidadDeTrabajo` en vez de `@Transactional` | Aceptada |
| [0007](0007-spring-data-jdbc-y-modelo-de-persistencia-separado.md) | Spring Data JDBC con un modelo de persistencia separado del de dominio | Sustituida por 0022 |
| [0008](0008-jwt-rs256-con-jwks.md) | JWT RS256 emitido por identidad y validado con JWKS | Aceptada |
| [0009](0009-api-con-prefijo-por-contexto-y-filtros-por-id.md) | API rediseñada: prefijo por contexto, filtros por id, RFC 9457 | Aceptada |
| [0010](0010-consultas-cqrs-reconstruible-por-replay.md) | `consultas` como lado de lectura CQRS reconstruible reproduciendo Kafka | Aceptada |
| [0011](0011-leer-lo-que-acabas-de-escribir.md) | Leer lo que acabas de escribir con secuencias (`Gastos-Esperar`) | Aceptada |
| [0012](0012-importe-bigdecimal.md) | Dinero como `BigDecimal` con escala 2 | Aceptada |
| [0013](0013-nombres-unicos-normalizados.md) | Nombres únicos por usuario sobre su forma normalizada | Aceptada |
| [0014](0014-prioridad-explicita.md) | Prioridad explícita en categorías y etiquetas | Aceptada |
| [0015](0015-duplicados-por-conteo.md) | Detección de duplicados por conteo de claves | Aceptada |
| [0016](0016-fechas-estrictas.md) | Fechas estrictas al interpretar extractos | Aceptada |
| [0017](0017-reetiquetar-respeta-lo-manual.md) | Reetiquetar respeta las asignaciones manuales | Aceptada |
| [0018](0018-monorepo-maven-multimodulo.md) | Monorepo Maven multi-módulo | Aceptada |
| [0019](0019-gateway-webflux.md) | Spring Cloud Gateway en su variante WebFlux | Aceptada |
| [0020](0020-restauracion-v1-con-uuid-originales.md) | Respaldo y migración con el formato v1 conservando los UUID | Aceptada |
| [0021](0021-nombres-en-espanol.md) | Código, paquetes de capas y documentación en español | Aceptada |
| [0022](0022-persistencia-con-jdbcclient.md) | Persistencia con `JdbcClient` y SQL explícito (resultado del spike de F0) | Aceptada |
