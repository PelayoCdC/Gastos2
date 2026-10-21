# ADR 0004: Kafka con un topic por contexto y el usuario como clave

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

Los servicios se comunican con eventos de integración. Hay que elegir un broker y decidir cómo se
reparten los eventos en topics y particiones. El orden importa:
- un "documento eliminado" no puede procesarse antes que su "documento registrado";
- una clasificación no debe aplicarse sobre una transacción ya borrada.

Además, el lado de lectura (`consultas`) debe poder **reconstruirse desde cero** reproduciendo todos los
eventos ([0010](0010-consultas-cqrs-reconstruible-por-replay.md)).

## Opciones consideradas

- **Broker:**
  1. *RabbitMQ*: más sencillo, pero no conserva el historial.
  2. *Kafka*: un log persistente que se puede volver a leer desde el principio.
- **Topics:**
  1. Un topic por tipo de evento.
  2. Un topic por agregado.
  3. Un topic por contexto.
- **Clave de partición:**
  1. El id del agregado: orden por agregado.
  2. El id del usuario: orden por usuario dentro del contexto.

## Decisión

Kafka, en modo KRaft con un solo nodo en local:
- **Un topic por contexto productor:** `identidad.eventos`, `movimientos.eventos`,
  `clasificacion.eventos` e `importacion.eventos`.
- **Clave = `usuarioId`.** Así, todos los eventos de un usuario en un contexto están en la misma
  partición y llegan **en orden total**, también entre agregados distintos (el documento antes que sus
  transacciones).
- Cada topic tiene 3 particiones, `retention.ms=-1` (retención infinita) y su DLT `<topic>-dlt`.
- Los topics los crea el productor; la autocreación del broker está desactivada.

## Consecuencias

- ✅ Los consumidores pueden asumir el orden dentro de un contexto y por usuario, lo que simplifica
  mucho las proyecciones.
- ✅ Los topics se pueden reproducir desde el principio para reconstruir proyecciones.
- ⚠️ No hay orden **entre** contextos: una clasificación puede llegar a `consultas` antes que la
  transacción. Los consumidores lo toleran con filas parciales, versiones y lápidas (ver
  [conceptos transversales](../arquitectura/05-conceptos-transversales.md)).
- ⚠️ Con un único usuario intensivo, una partición concentra todo su tráfico. Para el volumen de una
  aplicación personal es irrelevante.
- ⚠️ La retención infinita convierte el volumen `datos-kafka` en un dato a respaldar.
