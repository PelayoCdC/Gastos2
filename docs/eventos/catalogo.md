# Catálogo de eventos de integración

Contrato publicado entre servicios: `compartido/contratos`, paquetes `es.gastos.contratos.<contexto>.v1`.
Un test comprueba que **cada tipo de evento del código aparece en este documento**. Si añades uno y no lo
documentas, el build falla.

## Sobre común

Cada mensaje de Kafka lleva:
- como **clave**, el `usuarioId`;
- como **valor**, el sobre en JSON;
- como **cabeceras**, `tipo`, `version` y `traceparent`.

| Campo | Significado |
|---|---|
| `eventoId` | UUID único del evento (para la idempotencia del inbox) |
| `tipo` | Nombre del evento, p. ej. `TransaccionRegistrada` |
| `contexto` | Servicio productor |
| `version` | Versión del contrato del evento (empieza en 1) |
| `secuencia` | Id del outbox del productor (creciente); para leer lo que acabas de escribir |
| `ocurridoEn` | Instante UTC |
| `usuarioId` | Propietario de los datos |
| `agregadoId` | Agregado que cambió |
| `versionAgregado` | Versión del agregado tras el cambio; los consumidores descartan las antiguas |
| `datos` | Contenido específico del evento |

**Garantías:**
- Entrega **al menos una vez**.
- **Orden por usuario dentro de cada topic.**
- **Sin orden entre topics.**

## Topics

| Topic | Productor | Consumidores |
|---|---|---|
| `identidad.eventos` | identidad | clasificacion |
| `movimientos.eventos` | movimientos | clasificacion, consultas |
| `clasificacion.eventos` | clasificacion | consultas |
| `importacion.eventos` | importacion | consultas |

**Mensajes fallidos.** Cada servicio consumidor tiene su DLT, `<servicio>.dlt` (por ejemplo,
`clasificacion.dlt`). Un mensaje que falla tres veces seguidas va ahí, con cabeceras que indican el topic,
la partición, el offset y el error originales, y la partición sigue avanzando.

## Eventos

Cada evento se documenta cuando se implementa, con su productor, sus consumidores, sus campos y qué hace
el consumidor si llega tarde o repetido. La muestra JSON aprobada de cada uno está en
`compartido/contratos/src/test/resources/contratos/`.

### identidad.eventos

#### `UsuarioRegistrado` (v1)

- **Productor:** identidad, desde el caso de uso `RegistrarUsuario`.
- **Consumidores:**
  - clasificacion (F5), que siembra el catálogo inicial del usuario.
- **Clave:** el `usuarioId` del usuario registrado. `agregadoId` es el mismo id y `versionAgregado` vale 1.

| Campo | Tipo | Descripción |
|---|---|---|
| `usuarioId` | UUID | Id del usuario nuevo |
| `email` | texto | Email en forma canónica (minúsculas) |
| `nombre` | texto | Nombre visible |

- **Si llega repetido:** clasificacion usa el inbox y la siembra es idempotente (`catalogo_sembrado`).
- **Si llega tarde:** clasificacion siembra de forma perezosa en la primera petición del usuario, así que
  el evento tardío no hace nada.
