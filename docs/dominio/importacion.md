# Contexto: importación

**Tipo:** subdominio de soporte, capa anticorrupción · **Servicio:** `servicios/importacion` · **Estado:** diseñado (se implementa en F7 y F10)

## Responsabilidad

Traducir el "idioma" de cada banco (hojas de cálculo con columnas propias, formatos de fecha, separadores
decimales, CSV en distintas codificaciones) al modelo limpio que entiende `movimientos`
([ADR 0003](../adr/0003-importacion-como-capa-anticorrupcion.md)). También orquesta la restauración de
respaldos v1 ([ADR 0020](../adr/0020-restauracion-v1-con-uuid-originales.md)).

## Modelo

### Agregado `FormatoBanco`
Antes se llamaba `ConfiguracionImportacion`.
- `nombre`: único normalizado.
- `filaInicio`: ≥ 1; es la primera fila con datos, contando desde 1.
- **VO `MapeoColumnas`:** índices empezando en 0.
  - `concepto`, `importe` y `fechaContable` son **obligatorios**, y se validan al guardar (antes, al
    importar).
  - `fechaValor` y `saldo` son opcionales.
- **VO `FormatoFecha`:** por defecto `dd/MM/yyyy`. Es estricto: `31/02/2024` es un error
  ([ADR 0016](../adr/0016-fechas-estrictas.md)).

### Hoja y celdas
Son el modelo neutral, independiente de POI.
- `Hoja` es una lista de filas de `Celda{texto, numero?, fecha?}`.
- Los adaptadores `LectorHojasPoi` (xlsx/xls) y `LectorCsv` producen una `Hoja`.
- **CSV:**
  - Codificación UTF-8 (sin BOM), con caída a windows-1252.
  - Separador `;`, `,` o tabulador, el más frecuente en las 5 primeras líneas.
  - Comillas con `""` como escape.

### Servicio de dominio `InterpreteExtracto` (puro)
Recibe `(Hoja, FormatoBanco)` y devuelve `{lineas: LineaExtracto[], errores: ErrorFila[]}`.
- Empieza en `filaInicio` y salta las filas vacías.
- **Importe:**
  - Se eliminan los caracteres `[^0-9,.-]`, y el último `,` o `.` es el separador decimal.
  - El resultado es `Importe` con escala 2.
  - Las celdas numéricas se leen tal cual.
- **Fechas:** las celdas de tipo fecha se leen directamente; el texto se interpreta con el
  `FormatoFecha`.
- **Errores:** cada fila errónea genera `"Fila N: …"` y no se importa.

### `Previsualizacion`
Las primeras 20 filas como texto, rellenadas al mismo ancho, más `totalFilas`. Se usa para elegir las
columnas al crear un formato.

## Casos de uso

| Caso de uso | Descripción |
|---|---|
| CRUD de `FormatoBanco` | Con propiedad por usuario |
| `Previsualizar` | Fichero → previsualización |
| `ImportarExtracto` | Formato y cuenta → `{documentoId, nombreDocumento, nombreArchivo, importadas, errores[], duplicados[]}`. Genera el `extractoId` y llama a `POST /internal/extractos` de movimientos, reenviando el JWT y reintentando solo ante errores de conexión o 503 |
| `RestaurarRespaldo` | SIMULAR o SUSTITUIR un JSON v1 repartiéndolo entre los servicios |

## Eventos publicados (`importacion.eventos`)

- `FormatoBancoGuardado`, `FormatoBancoEliminado`.
- `FormatosRestaurados`.

`consultas` los usa para la exportación v1.
