# ADR 0005: Outbox transaccional e inbox implementados a mano

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

Un servicio no puede escribir en su base de datos y en Kafka de forma atómica: si guarda y después
falla al publicar, el evento se pierde; si publica y después falla al guardar, se anuncia un cambio que
no existe (el problema de la *escritura dual*). Del lado consumidor, Kafka entrega *al menos una vez*:
un evento puede llegar repetido.

## Opciones consideradas

1. **Publicar directamente con `KafkaTemplate`** tras guardar. Es simple, pero incorrecto ante fallos.
2. **Spring Modulith** (registro de publicaciones de eventos con externalización a Kafka). Menos
   código, pero el mecanismo queda oculto y obliga a usar `ApplicationEventPublisher` dentro del flujo.
3. **Outbox e inbox propios** en un módulo compartido (`compartido/mensajeria`).

## Decisión

Opción 3, porque el patrón es parte de lo que se quiere aprender y tener a la vista.

- **Outbox:**
  - El caso de uso llama a `PublicadorEventos` dentro de la unidad de trabajo.
  - El adaptador traduce cada evento de dominio a su contrato de integración y lo inserta en la tabla
    `outbox` en la **misma transacción** que el cambio de negocio.
  - Un *relay*:
    - hace un sondeo cada 200 ms y además se despierta al confirmarse una transacción;
    - lee los pendientes en orden con `FOR UPDATE SKIP LOCKED`;
    - los envía con un productor idempotente (`acks=all`) y los marca como publicados.
- **Inbox:**
  - Los consumidores con efectos (sembrar el catálogo, clasificar al importar) registran
    `(evento_id, consumidor)` en la tabla `inbox` en la misma transacción que su efecto.
  - Si el evento ya estaba registrado, lo ignoran.
- **Proyecciones de consultas:** no usan inbox. Son idempotentes por versión, y un inbox impediría
  reproducir los topics.

## Consecuencias

- ✅ No hay cambio sin evento ni evento sin cambio (entrega al menos una vez, en orden por usuario).
- ✅ El mecanismo es explícito y testeable: un IT comprueba el camino tabla → Kafka.
- ⚠️ Es más código que mantener (~300 líneas) y hay que limpiar la tabla de lo ya publicado (un job
  borra lo de más de 7 días).
- ⚠️ El id del outbox se asigna al insertar y no al confirmar, así que dos transacciones concurrentes
  pueden publicarse en orden distinto al de su commit. Las versiones por agregado cubren el caso
  ([0010](0010-consultas-cqrs-reconstruible-por-replay.md)).
