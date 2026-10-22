# Contexto: importación

**Tipo:** subdominio de soporte, capa anticorrupción · **Servicio:** `servicios/importacion` (puerto 8084) ·
**Estado:** formatos, previsualización e importación implementados en F7; la restauración de respaldos llega en F10

## Responsabilidad

Traducir el "idioma" de cada banco (hojas de cálculo con columnas propias, formatos de fecha, separadores
decimales, CSV en distintas codificaciones) al modelo limpio que entiende `movimientos`
([ADR 0003](../adr/0003-importacion-como-capa-anticorrupcion.md)). También orquestará la restauración de
respaldos v1 ([ADR 0020](../adr/0020-restauracion-v1-con-uuid-originales.md)).

**Lo que no hace:** no guarda ficheros ni movimientos. El fichero se interpreta en memoria y las líneas
limpias viajan a movimientos, que es quien decide qué filas son nuevas, crea el documento y publica los
eventos. Así la regla de duplicados ([ADR 0015](../adr/0015-duplicados-por-conteo.md)) vive en un solo
sitio, junto a los datos con los que compara.

**Por qué es un servicio aparte.** POI, los charsets y las rarezas de cada banco son el código más
expuesto a cambios externos (un banco cambia su exportación y ya está). Aislándolo, un fallo de lectura
nunca afecta a la consulta de movimientos ni a la clasificación, y movimientos recibe siempre datos ya
tipados: fechas `LocalDate`, importes `BigDecimal`, nada de texto por interpretar.

## Modelo

### Agregado `FormatoBanco` (`dominio/formato`)
Antes se llamaba `ConfiguracionImportacion`.
- `nombre`: obligatorio, hasta 100 caracteres, **único por usuario en su forma normalizada**
  ([ADR 0013](../adr/0013-nombres-unicos-normalizados.md)): "Unicaja xlsx" y " UNICAJA  XLSX " chocan (409).
- `filaInicio`: ≥ 1; es la primera fila con datos, contando desde 1 como en la hoja de cálculo.
- **VO `MapeoColumnas`:** índices empezando en 0.
  - `concepto`, `importe` y `fechaContable` son **obligatorios** y se validan al guardar el formato
    (`MAPEO_INCOMPLETO`). En el back antiguo se descubría al importar, con un fichero ya subido.
  - `fechaValor` y `saldo` son opcionales. Ninguna columna puede ser negativa (`MAPEO_INVALIDO`).
- **VO `FormatoFecha`:** patrón de `DateTimeFormatter`, por defecto `dd/MM/yyyy`. Un patrón que no
  compila se rechaza al guardar (`FORMATO_FECHA_INVALIDO`). Es **estricto**
  ([ADR 0016](../adr/0016-fechas-estrictas.md)): `31/02/2024` es un error de fila, no el 29 de febrero.
  Para ello se compila con `ResolverStyle.STRICT` cambiando `y` por `u` (en modo estricto `y` es el año
  *de la era* y exigiría la era en el texto).
- Modificar sin cambios no anuncia nada; crear, modificar y eliminar anuncian el estado completo.

### Hoja y celdas (`dominio/extracto`)
Son el modelo neutral, independiente de POI.
- `Hoja` es una lista de filas de `Celda{texto, numero?, fecha?}`. Lo que no existe (columna fuera de
  la fila, fila fuera de la hoja, columna opcional no mapeada) se lee como `Celda.VACIA`.
- `texto` es lo que el usuario ve en su hoja; `numero` y `fecha` solo vienen cuando el fichero los
  distingue (celdas numéricas o de fecha de Excel). En un CSV todo es texto.
- Los adaptadores de `infraestructura/lectura` producen la `Hoja`:
  - **Excel** (`LectorExcel`, xlsx y xls con `WorkbookFactory`): primera hoja; texto formateado en
    español con `DataFormatter`; las fórmulas se leen por su último valor calculado (no se evalúan);
    se quitan las filas vacías del final, que POI materializa a veces.
  - **CSV** (`LectorCsv`, port del back antiguo y del móvil): UTF-8 con o sin BOM y caída a
    windows-1252; separador `;`, `,` o tabulador, el más frecuente en las 5 primeras líneas (en empate,
    el primero de esa lista); comillas con `""` como escape y saltos de línea dentro de comillas. Ya no
    pasa por un `Workbook` en memoria.
  - Un fichero que no se puede leer da 400 `FICHERO_ILEGIBLE`, nunca 500.

### Servicio de dominio `InterpreteExtracto` (puro)
Recibe `(Hoja, FormatoBanco)` y devuelve `Interpretacion{lineas[], errores[]}`.
- Empieza en `filaInicio` y salta en silencio las filas sin concepto, importe ni fecha contable.
- **Importe:** si la celda es numérica, su valor. Si es texto, se eliminan los caracteres `[^0-9,.-]` y
  el **último** `,` o `.` es el separador decimal: `1.234,56` y `1,234.56` son 1234.56, `3.00 EUR` es 3.
  El resultado es un `Importe` con escala 2.
- **Fechas:** las celdas de tipo fecha se usan tal cual; el texto se interpreta con el `FormatoFecha`.
- **Errores:** una fila con un obligatorio vacío o mal escrito, o con un opcional mal escrito, se
  descarta entera y queda como `ErrorFila{fila, "Fila N: …"}`. Los mensajes son los del back antiguo
  ("importe vacio o no numerico", "fecha con formato incorrecto: '31/02/2026'").
- **Las filas idénticas son líneas distintas.** El intérprete no deduplica: decidir si una fila ya
  estaba registrada es de movimientos ([ADR 0015](../adr/0015-duplicados-por-conteo.md)).

### `Previsualizacion`
Las primeras 20 filas como texto, rellenadas al mismo ancho, más `totalFilas`. Se usa para elegir las
columnas al crear un formato; no necesita formato ni cuenta.

## Casos de uso (`aplicacion`)

| Caso de uso | Descripción |
|---|---|
| `GestionFormatos` | Crear, modificar, consultar, listar y eliminar formatos. Propiedad por usuario: uno ajeno da 404 |
| `ImportarExtractos.previsualizar` | Fichero → `Previsualizacion` |
| `ImportarExtractos.importar` | Formato + cuenta + fichero → `{documentoId, nombreDocumento, nombreArchivo, importadas, errores[], duplicados[]}` |
| `RestaurarRespaldo` (F10) | SIMULAR o SUSTITUIR un JSON v1 repartiéndolo entre los servicios |

**Importar, paso a paso:**
1. Comprueba que el formato existe y es del usuario (404 si no). La cuenta no se comprueba aquí: es de
   movimientos, que la comprueba con el mismo JWT.
2. Lee el fichero (puerto `LectorHojas`) e interpreta la hoja con el formato.
3. Genera el `extractoId` y entrega las líneas a movimientos (puerto `RegistroMovimientos`,
   `POST /internal/extractos`). El `extractoId` es la clave de idempotencia y acaba siendo el id del
   documento.
4. Devuelve lo que registró movimientos (importadas y duplicadas) junto con los errores de
   interpretación.

**Por qué no hay unidad de trabajo en la importación.** Este servicio no escribe nada al importar: la
única transacción es la de movimientos. Si la respuesta se pierde por la red, el reintento con el mismo
`extractoId` devuelve el resultado guardado sin importar dos veces.

**Adaptador `RegistroMovimientosHttp`:**
- Reenvía el JWT del usuario (sin cuentas de servicio, [ADR 0008](../adr/0008-jwt-rs256-con-jwks.md)).
- Reintenta dos veces los errores de conexión (`@Retryable` de Spring Framework 7). Es seguro gracias
  al `extractoId`.
- Traduce las respuestas de movimientos a las excepciones del kernel con el **mismo `codigo`**: 404 →
  `RecursoNoEncontrado` (cuenta ajena o inexistente), 400 → `ExcepcionDominio`, 409 → `Conflicto`, y
  cualquier otra cosa (5xx, red caída tras los reintentos) → 503 `SERVICIO_NO_DISPONIBLE`.

## Límite de tamaño

10 MB por fichero, en dos sitios: el gateway lo corta antes de llegar al servicio (413) y el propio
servicio lo aplica con `spring.servlet.multipart.max-file-size` por si se le llama directamente (413
`FICHERO_DEMASIADO_GRANDE`).

## Eventos publicados (`importacion.eventos`)

- `FormatoBancoGuardado`, `FormatoBancoEliminado` (estado completo; ver el
  [catálogo](../eventos/catalogo.md)).
- `FormatosRestaurados` (F10).

`consultas` los usará para la exportación v1. La importación en sí no publica nada propio: los eventos
de la importación (`DocumentoRegistrado`, `TransaccionRegistrada` con `origen=IMPORTACION`) los publica
movimientos, que es quien registra los hechos.

## Persistencia

Una tabla, `formato_banco`, con `UNIQUE(usuario_id, nombre_normalizado)` en collation binaria y versión
para el bloqueo optimista (`FormatoRepositorioJdbc`, [ADR 0022](../adr/0022-persistencia-con-jdbcclient.md)).
Más las tablas comunes `outbox` e `inbox`.

## API (a través del gateway, `/api/importacion`)

| Método y ruta | Cuerpo | Respuesta | Errores |
|---|---|---|---|
| `GET /formatos` | — | 200 `[{id, nombre, filaInicio, columnaConcepto, columnaImporte, columnaFechaContable, columnaFechaValor, columnaSaldo, formatoFecha}]`, por nombre | — |
| `GET /formatos/{id}` | — | 200 formato | 404 |
| `POST /formatos` | `{nombre, filaInicio?=1, columnaConcepto, columnaImporte, columnaFechaContable, columnaFechaValor?, columnaSaldo?, formatoFecha?="dd/MM/yyyy"}` | 201 formato | 400 `VALIDACION`, `NOMBRE_INVALIDO`, `MAPEO_INCOMPLETO`, `MAPEO_INVALIDO`, `FILA_INICIO_INVALIDA`, `FORMATO_FECHA_INVALIDO`; 409 `NOMBRE_DUPLICADO` |
| `PUT /formatos/{id}` | igual que `POST` | 200 formato | los de `POST`, más 404 y 409 `MODIFICACION_CONCURRENTE` |
| `DELETE /formatos/{id}` | — | 204 | 404 |
| `POST /previsualizaciones` | multipart `archivo` | 200 `{nombreArchivo, totalFilas, filas: [[texto]]}` (20 filas como mucho) | 400 `FICHERO_VACIO`, `FICHERO_ILEGIBLE`; 413 |
| `POST /respaldos` | JSON v1; `?modo=SIMULAR` (por defecto) o `SUSTITUIR` | 200 `{modo, cuentas, documentos, transacciones, categorias, etiquetas, clasificadas, formatos, anomalias[]}` | 400 `RESPALDO_NO_VALIDO`; 503 si movimientos o clasificacion no responden |
| `POST /importaciones` | multipart `archivo` + `formatoId` + `cuentaId` + `nombre?` | 201 `{documentoId, nombreDocumento, nombreArchivo, importadas, errores: [{fila, mensaje}], duplicados: [{fila, fechaContable, concepto, importe}]}` | 400 (`FICHERO_*`, `PARAMETRO_INVALIDO`, o el `codigo` que dé movimientos); 404 formato o cuenta; 413; 503 `SERVICIO_NO_DISPONIBLE` si movimientos no responde |

`nombre` es el nombre del documento; si no se indica, se usa el del fichero. Las filas con errores no se
importan, pero no impiden importar las demás: el resultado las lista para que el usuario las revise.

Ejemplos: [`http/importacion.http`](../../http/importacion.http).

## Respaldos v1 (F10)

`POST /respaldos?modo=SIMULAR|SUSTITUIR` (por defecto SIMULAR) con el JSON v1 del back antiguo, del móvil
o de `GET /api/consultas/exportacion/v1` ([ADR 0020](../adr/0020-restauracion-v1-con-uuid-originales.md)).
Responde `{modo, cuentas, documentos, transacciones, categorias, etiquetas, clasificadas, formatos,
anomalias[]}`.

- **`TraductorRespaldoV1`** (capa anticorrupción, puro): lee ids y fechas, que vienen como texto, y
  descarta las transacciones sin id, fecha contable o importe **antes** de repartir el respaldo.
- **`RestaurarRespaldo`** reparte: movimientos y clasificacion por su `PUT /internal/respaldos/v1`
  (`RestauracionRemotaHttp`, con el JWT reenviado y reintentos ante fallos de red, que es seguro porque
  sustituir es idempotente) y los formatos aquí mismo (`PlanFormatos`). Con SIMULAR, cada servicio
  ensaya sin escribir.
- **Orden y atomicidad:** movimientos → clasificacion → formatos. Cada paso es atómico en su servicio,
  pero el conjunto no; si uno falla, se repite la restauración entera y queda bien porque cada paso
  sustituye.
- **Formatos** (`configuracionesImportacion` en el v1): los que no tienen concepto, importe o fecha se
  descartan (el antiguo los admitía y fallaban al importar), un nombre repetido se queda con el primero y
  un patrón de fecha no válido pasa a `dd/MM/yyyy`. Se publica `FormatosRestaurados` y después cada
  `FormatoBancoGuardado`.
- La respuesta lleva las `Gastos-Secuencia` de los tres servicios para leer el resultado en consultas
  con `Gastos-Esperar`.
