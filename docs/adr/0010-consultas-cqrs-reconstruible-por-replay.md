# ADR 0010: `consultas` como lado de lectura CQRS reconstruible reproduciendo Kafka

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

La rejilla de transacciones y los informes combinan datos de tres contextos: hechos de `movimientos`,
clasificación y catálogo de `clasificacion`, y formatos de `importacion`. Con una base de datos por
servicio no se puede hacer un `JOIN` entre ellos. Además, las consultas de informes son pesadas y
cambian a menudo (nuevos análisis).

## Opciones consideradas

1. **Composición en tiempo de consulta:** el gateway o un BFF piden a cada servicio y combinan. Es lento,
   y filtrar u ordenar sobre datos combinados es inviable.
2. **Réplicas de nombres en cada servicio** y que cada uno sirva sus informes. Se duplicaría lógica, y
   seguiría habiendo consultas cruzadas.
3. **Servicio de lectura (CQRS)** con su propio modelo, alimentado por todos los eventos.

## Decisión

Opción 3: el servicio `consultas`.

- **Proyecciones en su propia base de datos.** Las tablas tienen la forma que necesitan las consultas.
  `transaccion_vista` reúne hechos y clasificación, y el catálogo se une por id.
- **Tolerancia al desorden entre contextos:**
  - **Filas parciales:** una transacción puede recibir antes su clasificación que su registro, y la fila
    solo se muestra cuando `registrada = true`.
  - **Una versión por origen**, con actualizaciones protegidas (`WHERE version_x < ?`).
  - **Lápidas** (`eliminada = true`) para que un evento tardío no resucite una fila borrada.
  - **Eventos de conjunto** aplicados con SQL por conjuntos (`WHERE documento_id = ?`).
- **Reconstrucción por reproducción del log:**
  - La propiedad `gastos.consultas.proyeccion.version=N` determina el grupo de consumidores
    `consultas-vN`.
  - Al cambiar N, el servicio vacía sus tablas y relee los topics desde el principio (retención
    infinita, [0004](0004-kafka-topic-por-contexto-clave-usuario.md)).
- **Plan B** (F11): cada productor ofrece `POST /internal/admin/republicar-estado` para reemitir
  instantáneas si se perdiera el log.

## Consecuencias

- ✅ Las consultas son SQL directo sobre tablas pensadas para ellas. Añadir un informe no toca a los
  productores.
- ✅ Cambiar el esquema de lectura es barato: se sube N y se reconstruye. Las proyecciones son
  desechables.
- ⚠️ Hay consistencia eventual: lo escrito tarda unos milisegundos en verse. Se mitiga con
  [0011](0011-leer-lo-que-acabas-de-escribir.md).
- ⚠️ La reconstrucción depende de que el volumen de Kafka se conserve y de que **todas** las escrituras
  pasen por casos de uso y outbox, incluida la migración. Nunca se inserta directamente en la base de
  datos de un servicio.

## Notas de implementación (F8, 2026-10-22)

- **Proyección por lotes.** El consumidor recibe hasta 500 mensajes y los aplica en una sola
  transacción; si el lote falla, los repite uno a uno y señala el culpable con
  `BatchListenerFailedException` para que vaya al DLT sin bloquear la partición. Reaplicar los buenos no
  hace daño porque cada operación es idempotente por versión.
- **Esqueleto + `UPDATE` protegido.** Cada escritura empieza con `INSERT IGNORE` de la fila (id y
  usuario) y sigue con `UPDATE ... WHERE version_x < :version`. Si el `UPDATE` no toca ninguna fila, el
  evento era antiguo y tampoco se aplican sus efectos de conjunto.
- **El grupo ya es `consultas-v${gastos.consultas.proyeccion.version}`**; vaciar las tablas al cambiar
  la versión llega en F9.
