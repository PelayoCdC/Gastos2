# Contexto: clasificación ★ (core domain)

**Tipo:** subdominio principal · **Servicio:** `servicios/clasificacion` · **Estado:** diseñado (se implementa en F5–F6)

## Responsabilidad

El catálogo de cada usuario (categorías y etiquetas con sus ocurrencias y prioridad) y la
**clasificación de cada transacción**: qué categoría y qué etiquetas tiene, y por qué. Aquí viven las
reglas que dan valor a la aplicación: clasificación automática, cascada, propagación, aplicación masiva
y reetiquetado.

## Modelo

### Agregado `Categoria`
- `nombre`: único normalizado; `color` (`#rrggbb` o nulo); `descripcion`.
- `ocurrencias`: lista de VOs `Ocurrencia`.
- `prioridad`: entero; menor = antes ([ADR 0014](../adr/0014-prioridad-explicita.md)).

### Agregado `Etiqueta`
- `nombre`: único normalizado; `descripcion`; `ocurrencias`; `prioridad`.
- `categoriaId?`: la categoría que aplica la **cascada**. Debe pertenecer al mismo usuario.

### VO `Ocurrencia`
- Guarda el texto tal cual, **incluidos los espacios de los extremos** (`"bar "`, `" ibi"`).
- Compara con su forma normalizada: sin tildes, en minúsculas y **sin recortar**.
  - **Por qué no se recorta:** el catálogo por defecto usa esos espacios para evitar falsos positivos
    ("bar " no debe casar con "barcelona"). El back antiguo los recortaba al editar y rompía esas reglas
    sin avisar.
- Se rechaza si está en blanco. Las repetidas (por su forma normalizada) se eliminan.

### Agregado `ClasificacionTransaccion` (id = id de la transacción)
- **Copia de origen**, escrita solo por los eventos de movimientos: `usuarioId`, `documentoId`,
  `concepto`, `claveConcepto`, `versionOrigen` y `eliminada`.
- **Asignación**, escrita por comandos y reglas:
  - `categoriaId?` con su `origenCategoria`;
  - `etiquetas`: conjunto ordenado, sin repetidos, cada una con su origen;
  - `autoClasificada`.
- **Orígenes posibles:** AUTOMATICA, CASCADA, MANUAL, PROPAGACION, MASIVA o MIGRACION.

## Reglas (servicios de dominio puros)

### `MotorClasificacion`
Trabaja sobre una instantánea del catálogo (`CatalogoReglas`) ordenada por prioridad. Solo intervienen
las entradas con ocurrencias.

1. **Etiquetar:**
   - Añade todas las etiquetas con alguna ocurrencia contenida en el concepto normalizado.
   - No repite etiquetas.
2. **Cascada:**
   - Solo actúa si la transacción no tiene categoría.
   - Toma la categoría de la etiqueta asignada de mayor prioridad que tenga una.
3. **Categorizar:**
   - Solo actúa si no hay categoría.
   - Asigna la primera categoría por prioridad cuya ocurrencia esté en el concepto.
   - **Nunca pisa** una categoría existente.

| Modo | Pasos |
|---|---|
| IMPORTACION | 1 → 2 → 3 |
| ETIQUETAS | 1 → 2 |
| CATEGORIAS | 3 |
| REINICIAR | Quita las asignaciones AUTOMATICA y CASCADA ([ADR 0017](../adr/0017-reetiquetar-respeta-lo-manual.md)), luego 1 → 2 → 3; con `forzar`, quita todo |

### `Propagador`
- **Similares:** transacciones del mismo usuario con la misma `ClaveConcepto`, excluida la propia.
- **Propagar:**
  - Copia la categoría de la base si la tiene (la pisa en las similares).
  - **Añade** las etiquetas de la base.
  - Devuelve cuántas transacciones cambiaron, así que repetir la operación devuelve 0.

### Catálogo inicial
- Se carga de `catalogo-inicial.json`, cuyo orden fija la prioridad: 12 categorías y 23 etiquetas, 20 de
  ellas enlazadas a una categoría.
- Se siembra al recibir `UsuarioRegistrado`, o de forma perezosa en la primera petición si el evento aún
  no ha llegado.
- Es idempotente gracias a la tabla `catalogo_sembrado`.

## Casos de uso

| Grupo | Casos de uso |
|---|---|
| Catálogo | CRUD de categorías y etiquetas; `ReordenarCategorias` y `ReordenarEtiquetas`; `ExportarDefiniciones` e `ImportarDefiniciones` (upsert por nombre normalizado) |
| Borrados | `EliminarCategoria` (la desasigna de transacciones **y de etiquetas**, en una transacción local); `EliminarEtiqueta` |
| Clasificación | `ClasificarTransaccion` (manual, con *read-through* si la copia aún no existe); `ConsultarClasificacion`; `ContarSimilares`; `Propagar`; `AplicarMasivamente` (ANADIR o SUSTITUIR; los ids ajenos se ignoran y se cuentan) |
| Automática | `EjecutarClasificacionAutomatica(modo, forzar)` |
| Por eventos | `SembrarCatalogoInicial`; `SincronizarTransacciones` (copia y clasificación automática de lo importado); `OlvidarTransacciones` |
| Respaldo | `RestaurarRespaldo` |

## Eventos

- **Publica** (`clasificacion.eventos`):
  - `CategoriaGuardada`, `CategoriaEliminada`;
  - `EtiquetaGuardada`, `EtiquetaEliminada`;
  - `TransaccionClasificada` (con el motivo del cambio);
  - `ClasificacionRestaurada`.
- **Consume:** `UsuarioRegistrado` y los eventos de transacciones y documentos de movimientos.
