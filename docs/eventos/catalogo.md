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

### movimientos.eventos

Productor: movimientos. La clave es el usuario propietario, y `agregadoId` es el id de la cuenta, el
documento o la transacción. Los importes viajan como número JSON con 2 decimales.

| Evento | Cuándo | Campos de `datos` | Consumidores |
|---|---|---|---|
| `CuentaGuardada` | Crear o modificar una cuenta | `cuentaId`, `nombre`, `banco`, `descripcion` (estado completo) | consultas |
| `CuentaEliminada` | Borrar una cuenta. Si se borra en cascada (`conMovimientos=true`), antes se emiten los `DocumentoEliminado` y `TransaccionEliminada` de su contenido | `cuentaId` | consultas |
| `DocumentoRegistrado` | Importar un extracto. Va **antes** que las `TransaccionRegistrada` de sus filas | `documentoId`, `cuentaId`, `nombre`, `nombreArchivo`, `fecha` | consultas |
| `DocumentoRenombrado` | Renombrar un documento | `documentoId`, `nombre` | consultas |
| `DocumentoReasignado` | Mover un documento **y todas sus transacciones** a otra cuenta (evento de conjunto) | `documentoId`, `cuentaIdAnterior`, `cuentaIdNueva` | consultas |
| `DocumentoEliminado` | Borrar un documento **y todas sus transacciones** (evento de conjunto: no llega un `TransaccionEliminada` por cada una) | `documentoId` | clasificacion, consultas |
| `TransaccionRegistrada` | Alta manual, fila importada o restauración | `transaccionId`, `origen` (IMPORTACION, MANUAL o MIGRACION), `cuentaId`, `documentoId`, `fechaContable`, `fechaValor`, `concepto`, `nota`, `importe`, `saldo`, `orden`, `ordenDocumento` | clasificacion, consultas |
| `TransaccionCorregida` | Editar los hechos de una transacción | `transaccionId`, `cuentaId`, `fechaContable`, `fechaValor`, `concepto`, `nota`, `importe` | clasificacion, consultas |
| `TransaccionEliminada` | Borrar una transacción concreta | `transaccionId`, `documentoId` | clasificacion, consultas |
| `MovimientosRestaurados` | Restaurar un respaldo (F10): sustituye **todos** los movimientos del usuario. Evento de conjunto que va **antes** que los `CuentaGuardada`, `DocumentoRegistrado` y `TransaccionRegistrada` (origen MIGRACION) de lo restaurado, que conservan sus ids. `agregadoId` es el usuario | `cuentas`, `documentos`, `transacciones` (cuántos se restauran) | consultas (clasificacion lo ignora) |

**Qué hacen los consumidores:**
- **Orden:** el de cada usuario está garantizado dentro del topic. Por eso un `DocumentoEliminado` nunca
  llega antes que el `DocumentoRegistrado` del mismo documento.
- **Eventos de conjunto:** se aplican con SQL por conjuntos (`WHERE documento_id = ?`).
- **Eventos repetidos o tardíos:** las proyecciones comparan `versionAgregado` y descartan las versiones
  que ya tienen o anteriores. Las lápidas evitan que un evento tardío resucite una fila borrada.

### clasificacion.eventos

Productor: clasificacion. La clave es el usuario propietario.

| Evento | Cuándo | Campos de `datos` | Consumidores |
|---|---|---|---|
| `CategoriaGuardada` | Crear, modificar o reordenar una categoría, o sembrar el catálogo inicial | `categoriaId`, `nombre`, `color`, `descripcion`, `ocurrencias[]`, `prioridad` (estado completo) | consultas |
| `CategoriaEliminada` | Borrar una categoría. Evento de conjunto: las transacciones que la tenían quedan sin categoría. Las etiquetas que la aplicaban en cascada llegan **antes** como `EtiquetaGuardada` sin categoría | `categoriaId` | consultas |
| `EtiquetaGuardada` | Crear, modificar o reordenar una etiqueta, quitarle la categoría o sembrar el catálogo | `etiquetaId`, `nombre`, `descripcion`, `ocurrencias[]`, `categoriaId` (cascada, o nulo), `prioridad` | consultas |
| `EtiquetaEliminada` | Borrar una etiqueta. Evento de conjunto: se quita de todas las transacciones | `etiquetaId` | consultas |
| `TransaccionClasificada` | Cambia la categoría o las etiquetas de una transacción: clasificación automática al importar, asignación manual, propagación, aplicación masiva, reetiquetado o restauración | `transaccionId`, `categoriaId` (o nulo), `etiquetaIds[]` (en orden), `motivo` (MANUAL, AUTOMATICA, PROPAGACION, MASIVA, REINICIO o MIGRACION) | consultas |
| `ClasificacionRestaurada` | Restaurar un respaldo (F10): sustituye el catálogo y las clasificaciones del usuario. Evento de conjunto que va **antes** que los `CategoriaGuardada`, `EtiquetaGuardada` y `TransaccionClasificada` (motivo MIGRACION) restaurados. `agregadoId` es el usuario | `categorias`, `etiquetas`, `clasificadas` | consultas |

Las ocurrencias viajan tal cual, **con sus espacios** (`"bar "`): son parte del significado de la regla.

`TransaccionClasificada` lleva el **estado completo** de la clasificación, no un cambio parcial: el
consumidor sustituye lo que tenía si `versionAgregado` es mayor. Si llega antes que el
`TransaccionRegistrada` de la misma transacción (topics distintos, sin orden entre ellos), consultas
guarda la parte de clasificación de la fila y la muestra cuando llegan los hechos.

### importacion.eventos

Productor: importacion. La clave es el usuario propietario.

| Evento | Cuándo | Campos de `datos` | Consumidores |
|---|---|---|---|
| `FormatoBancoGuardado` | Crear o modificar un formato de banco | `formatoId`, `nombre`, `filaInicio` (desde 1), `columnaConcepto`, `columnaImporte`, `columnaFechaContable` (desde 0), `columnaFechaValor?`, `columnaSaldo?`, `formatoFecha` | consultas (exportación v1) |
| `FormatoBancoEliminado` | Borrar un formato | `formatoId` | consultas |
| `FormatosRestaurados` | Restaurar un respaldo (F10): sustituye los formatos del usuario. Va **antes** que los `FormatoBancoGuardado` restaurados. `agregadoId` es el usuario | `formatos` | consultas |

Importar un extracto **no** emite eventos en importacion. El resultado lo registra movimientos, que
publica `DocumentoRegistrado` y las `TransaccionRegistrada`.
