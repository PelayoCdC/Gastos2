# Comparativa con el back antiguo (`gastos-back`)

Qué cambia respecto al monolito y por qué. Las funcionalidades se mantienen todas. Los cambios de
comportamiento marcados con ⚑ son **intencionados**: corrigen defectos y los decidió el usuario.

## Arquitectura

| Aspecto | gastos-back | Gastos2 | Por qué |
|---|---|---|---|
| Despliegue | 1 jar | 6 servicios + gateway | [ADR 0001](adr/0001-microservicios-por-contexto-acotado.md) |
| Módulos | 9, uno por entidad, con ciclos | 5 contextos acotados sin dependencias de código entre ellos | [Mapa de contextos](dominio/mapa-de-contextos.md) |
| Base de datos | Una, compartida, con FKs entre módulos | Una por servicio; entre contextos solo ids | ADR 0001 |
| Capas | `domain/application/infrastructure` nominales; aplicación importaba infraestructura | `dominio/aplicacion/infraestructura` verificadas con ArchUnit | [ADR 0006](adr/0006-unidad-de-trabajo-en-vez-de-transactional.md) |
| Entidades | JPA anémicas con `@Data` y setters públicos | Agregados con comportamiento, invariantes y eventos; persistencia separada | [ADR 0022](adr/0022-persistencia-con-jdbcclient.md) |
| Transacciones | `@Transactional` en servicios | Puerto `UnidadDeTrabajo` | ADR 0006 |
| Integración | Llamadas en memoria | Eventos en Kafka (outbox e inbox) y REST interno donde hace falta respuesta | [ADR 0004](adr/0004-kafka-topic-por-contexto-clave-usuario.md), [0005](adr/0005-outbox-e-inbox-propios.md) |
| Informes | Consultas JPQL sobre las tablas de escritura | Servicio CQRS con proyecciones reconstruibles | [ADR 0010](adr/0010-consultas-cqrs-reconstruible-por-replay.md) |
| Seguridad | JWT HS256 con secreto compartido; sin validar emisor | RS256 con JWKS; emisor y audiencia validados | [ADR 0008](adr/0008-jwt-rs256-con-jwks.md) |
| Aislamiento por usuario | Responsabilidad del controlador | Responsabilidad del caso de uso | ADR 0006 |
| Fecha actual | `LocalDate.now()` repartido por el código | `Clock` inyectado (ArchUnit lo exige) | Reproducibilidad en tests |

## API ([ADR 0009](adr/0009-api-con-prefijo-por-contexto-y-filtros-por-id.md))

| gastos-back | Gastos2 |
|---|---|
| `POST /x/search` con criterio en el cuerpo y `PageImpl` de Spring | `GET` con parámetros y `Pagina<T>` propia |
| `POST /transacciones/search` y `POST /transacciones/exportar` | `GET /consultas/transacciones` y `GET /consultas/transacciones/exportacion`, con los mismos parámetros |
| Filtros de informes por nombre (problema con las comas) | Filtros por id |
| Números y fechas como texto en dos formatos | Números JSON (dinero con 2 decimales) y fechas ISO |
| Errores con el JSON por defecto de Boot; 500 con entradas malformadas | RFC 9457 con `codigo`; 400, 404, 409, 413 o 415 según el caso |
| `DELETE` devuelve 200 vacío | 204 |
| Crear una transacción: un `PUT` con categoría y etiquetas por nombre | `POST /movimientos/transacciones` y `PUT /clasificacion/transacciones/{id}` |
| `/configuraciones-importacion` | `/importacion/formatos` |
| `POST /importaciones/precarga` | `POST /importacion/previsualizaciones` |
| `POST /importaciones` con `idConfiguracion` e `idCuenta`; `errores` y `duplicados` como listas de textos | `POST /importacion/importaciones` con `formatoId` y `cuentaId`; `errores: [{fila, mensaje}]` y `duplicados: [{fila, fechaContable, concepto, importe}]` |

## Comportamiento del dominio

| | gastos-back | Gastos2 | Referencia |
|---|---|---|---|
| ⚑ Dinero | `float` (pierde céntimos, sumas inexactas) | `BigDecimal` escala 2 / `DECIMAL(15,2)` | [ADR 0012](adr/0012-importe-bigdecimal.md) |
| ⚑ Saldo de transacción sin dato | 0 (falseaba el saldo de la cuenta) | nulo; el saldo de la cuenta ignora los nulos | ADR 0012 |
| ⚑ Nombres repetidos | Permitidos; luego fallaban con 500 | Únicos por usuario sobre la forma normalizada; 409 | [ADR 0013](adr/0013-nombres-unicos-normalizados.md) |
| ⚑ "Primera coincidencia" de categoría y cascada | Orden de la base de datos (arbitrario) | Prioridad explícita y reordenable | [ADR 0014](adr/0014-prioridad-explicita.md) |
| ⚑ Filas idénticas en un mismo extracto | Se importaba solo una | Se importan todas; al reimportar, todas duplicadas | [ADR 0015](adr/0015-duplicados-por-conteo.md) |
| ⚑ Fechas imposibles (31/02) | Se corregían en silencio | Error de fila | [ADR 0016](adr/0016-fechas-estrictas.md) |
| ⚑ Reetiquetar | Borraba también lo manual | Respeta lo manual; `forzar` para borrar todo | [ADR 0017](adr/0017-reetiquetar-respeta-lo-manual.md) |
| ⚑ Borrar una categoría usada por etiquetas | 500 (FK) | Se desasigna de transacciones y etiquetas | [clasificación](dominio/clasificacion.md) |
| ⚑ Borrar una cuenta con movimientos | 500 (FK) | 409, o borrado en cascada explícito | [movimientos](dominio/movimientos.md) |
| ⚑ Editar ocurrencias | Recortaba espacios y rompía `"bar "` o `" ibi"` | Los espacios se conservan siempre | [clasificación](dominio/clasificacion.md) |
| ⚑ Formato de banco incompleto | Se detectaba al importar (400) | Se valida al guardar, igual que el patrón de fecha | [importación](dominio/importacion.md) |
| ⚑ Fichero que no es Excel ni CSV | 400 genérico o 500 según el fallo de POI | 400 `FICHERO_ILEGIBLE` | [importación](dominio/importacion.md) |
| ⚑ Fórmulas en el Excel | Se leía el texto de la fórmula y la fila daba error | Se lee el último valor calculado | [importación](dominio/importacion.md) |
| ⚑ Contraseña / nombre | Solo "no vacía"; nombre opcional | Al menos 8 caracteres; nombre obligatorio | [identidad](dominio/identidad.md) |
| ⚑ Evolución del saldo | Agrupaba por **nombre** de cuenta (fusionaba cuentas homónimas) y un saldo desconocido contaba como 0 | Agrupa por id e ignora los saldos desconocidos | [consultas](dominio/consultas.md) |
| ⚑ Importes de los informes | Textos con dos decimales calculados con `double` | Números exactos (`BigDecimal`) | [consultas](dominio/consultas.md) |
| ⚑ Composición mensual por categoría | Meses ordenados como texto ("10" antes que "9") | Orden cronológico | [consultas](dominio/consultas.md) |
| ⚑ Recurrentes "activos" | Calculado con `LocalDate.now()` | `Clock` inyectado (probado con fechas fijas); periodicidad como enumerado | [consultas](dominio/consultas.md) |
| Reconstruir los informes | Imposible: se calculaban sobre las tablas de escritura | Las proyecciones se rehacen desde Kafka subiendo una versión | [ADR 0010](adr/0010-consultas-cqrs-reconstruible-por-replay.md) |
| ⚑ Ordenación de la rejilla | Sin desempate; un campo desconocido daba 500 | Desempate por `orden` e `id`; lista blanca de campos (400 `ORDEN_INVALIDO`) | [consultas](dominio/consultas.md) |
| ⚑ Excel de transacciones | Fechas como texto, saldo desconocido como 0 | Fechas e importes reales; saldo desconocido vacío; añade fecha valor, nota y documento al final | [consultas](dominio/consultas.md) |
| Leer justo después de guardar | Inmediato (una sola base de datos) | Consistencia eventual con `Gastos-Esperar` (espera hasta 3 s) | [ADR 0011](adr/0011-leer-lo-que-acabas-de-escribir.md) |
| Clasificación automática | Al importar (síncrona) | Al importar (asíncrona, milisegundos después) | [ADR 0002](adr/0002-asignaciones-en-clasificacion.md) |
| Exportación v1 para el móvil | `GET /migracion/exportar` | `GET /api/consultas/exportacion/v1` (mismo formato) | [ADR 0020](adr/0020-restauracion-v1-con-uuid-originales.md) |
| Restaurar un respaldo | No existía en el back (solo en el móvil) | `POST /api/importacion/respaldos` (SIMULAR o SUSTITUIR) | ADR 0020 |

## Se mantiene igual

- **Normalización** para comparar textos: NFD, sin marcas y en minúsculas. Ahora es explícita en el kernel.
- **Clave de concepto** y el patrón de fechas que elimina.
- **Lectura de CSV:** codificación, separador y comillas.
- **Interpretación de importes:** el último separador es el decimal.
- **Algoritmos de informes y análisis:** umbrales de recurrentes, detección de Bizum, top de conceptos.
- **Orden de clasificación:** etiquetas, después cascada, después categorías. La categoría nunca se pisa.
- **Propagación:** pisa la categoría y añade las etiquetas.
- **Catálogo inicial:** 12 categorías y 23 etiquetas con sus ocurrencias.
