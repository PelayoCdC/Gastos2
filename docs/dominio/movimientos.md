# Contexto: movimientos

**Tipo:** subdominio de soporte · **Servicio:** `servicios/movimientos` · **Estado:** diseñado (se implementa en F3–F4)

## Responsabilidad

Registro fiel de los **hechos bancarios**: cuentas, documentos importados y transacciones (fechas,
concepto, importe, saldo, nota). Detecta los duplicados al importar y calcula el saldo de cada cuenta.
No sabe nada de categorías ni etiquetas ([ADR 0002](../adr/0002-asignaciones-en-clasificacion.md)) ni
de formatos de banco ([ADR 0003](../adr/0003-importacion-como-capa-anticorrupcion.md)).

## Modelo

### Agregado `Cuenta`
- `nombre`: único por usuario sobre su forma normalizada ([ADR 0013](../adr/0013-nombres-unicos-normalizados.md)).
- `banco`, `descripcion`: opcionales.
- **Borrado:**
  - Con documentos o transacciones da 409 `CUENTA_CON_MOVIMIENTOS`.
  - Con `conMovimientos=true` borra en cascada documentos y transacciones, emitiendo sus eventos.
  - Antes fallaba con un 500.

### Agregado `Documento`
Es el registro de una importación.
- `cuentaId`; `nombre` (editable); `nombreArchivo` (el original, inmutable); `fecha` (de la importación,
  con el `Clock`).
- `resultadoRegistro`: el resultado de la importación, guardado para responder igual a un reintento con
  el mismo `extractoId`.
- **Reasignar a otra cuenta** mueve todas sus transacciones.
- **Borrarlo** borra sus transacciones.

### Agregado `Transaccion`
| Campo | Notas |
|---|---|
| `cuentaId?`, `documentoId?` | Las manuales no tienen documento. **Invariante:** si tiene documento, su cuenta es la del documento |
| `fechaContable` | Obligatoria |
| `fechaValor?` | |
| `concepto` | ≤ 500; puede estar vacío |
| `nota` | ≤ 500 |
| `importe` | `Importe` (kernel), obligatorio |
| `saldo?` | `Importe`; nulo si el extracto no trae saldo o si es manual |
| `orden` | Fila del extracto, empezando en 1 (0 en manuales) |
| `ordenDocumento` | 1..N entre las filas aceptadas |
| `origen` | IMPORTACION, MANUAL o MIGRACION |
| `claveDuplicado` | Fecha, importe y concepto (minúsculas, espacios colapsados). Fija en las importadas; se recalcula al editar las manuales |

### Servicio de dominio `DetectorDuplicados`
Funciona por conteo de claves ([ADR 0015](../adr/0015-duplicados-por-conteo.md)):
- **Ámbito:** la cuenta destino más las transacciones sin cuenta.
- Las primeras `existentes(k)` filas con la clave `k` son duplicadas y el resto se importa.

### Saldo de una cuenta
Es el `saldo` de la última transacción **con saldo**: la de fecha contable más reciente y, a igual
fecha, la de mayor `orden`.

## Casos de uso

| Grupo | Casos de uso |
|---|---|
| Cuentas | `CrearCuenta`, `ModificarCuenta`, `EliminarCuenta`, `ConsultarCuentas` (con saldo y número de transacciones) |
| Documentos | `RenombrarDocumento`, `ReasignarCuentaDocumento`, `EliminarDocumento`, `ConsultarDocumentos` |
| Transacciones | `RegistrarTransaccionManual`, `CorregirTransaccion`, `EliminarTransaccion`, `ConsultarTransaccion` |
| Internos | `RegistrarExtracto` (bloquea la fila de la cuenta; idempotente por `extractoId`), `ConsultarParaClasificacion`, `RestaurarRespaldo` |

## Eventos publicados (`movimientos.eventos`)

- `CuentaGuardada`, `CuentaEliminada`.
- `DocumentoRegistrado`, `DocumentoRenombrado`, `DocumentoReasignado`, `DocumentoEliminado`.
- `TransaccionRegistrada`, `TransaccionCorregida`, `TransaccionEliminada`.
- `MovimientosRestaurados`.

El detalle está en el [catálogo de eventos](../eventos/catalogo.md).
