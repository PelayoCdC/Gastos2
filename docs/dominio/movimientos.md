# Contexto: movimientos

**Tipo:** subdominio de soporte · **Servicio:** `servicios/movimientos` · **Estado:** ✅ cuentas, documentos y transacciones manuales (F3) · importación de extractos en F4

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

## API (a través del gateway: `/api/movimientos`)

| Método y ruta | Descripción | Respuesta |
|---|---|---|
| `GET /cuentas` | Cuentas del usuario, ordenadas por nombre, con `saldo` y `numeroTransacciones` | 200 `[ResumenCuenta]` |
| `GET /cuentas/{id}` | Una cuenta | 200 / 404 |
| `POST /cuentas` | `{nombre, banco?, descripcion?}` | 201 + `Location`; 409 `NOMBRE_DUPLICADO` |
| `PUT /cuentas/{id}` | Igual que el alta | 200; 404; 409 |
| `DELETE /cuentas/{id}[?conMovimientos=true]` | Borra; con movimientos y sin el parámetro, 409 `CUENTA_CON_MOVIMIENTOS` | 204 |
| `GET /documentos?cuentaId=&pagina=&tamano=` | Documentos, más recientes primero, con cuenta y número de transacciones | 200 `Pagina<ResumenDocumento>` |
| `GET /documentos/{id}` | Un documento | 200 / 404 |
| `PUT /documentos/{id}/nombre` | `{nombre}` | 200 |
| `PUT /documentos/{id}/cuenta` | `{cuentaId}`: mueve el documento **y sus transacciones** | 200 |
| `DELETE /documentos/{id}` | Borra el documento **y sus transacciones** | 204 |
| `POST /transacciones` | Alta manual `{cuentaId?, fechaContable, fechaValor?, concepto?, nota?, importe}` | 201 `DatosTransaccion` |
| `GET /transacciones/{id}` | Hechos de una transacción | 200 / 404 |
| `PUT /transacciones/{id}` | Corrige los hechos editables. En una importada, la cuenta solo cambia con su documento (400 `CUENTA_DE_TRANSACCION_IMPORTADA`) | 200 |
| `DELETE /transacciones/{id}` | Borra una transacción | 204 |

Ejemplos: [`http/movimientos.http`](../../http/movimientos.http).

## Implementación (F3)

| Pieza | Clases |
|---|---|
| Agregados | `dominio/cuenta/Cuenta`, `dominio/documento/Documento`, `dominio/transaccion/Transaccion` (con `Transaccion.Datos` como estado completo) |
| VOs | `NombreCuenta`, `NombreDocumento`, `Concepto`, `ClaveDuplicado`, `Origen`, ids tipados (`CuentaId`, `DocumentoId`, `TransaccionId`) |
| Casos de uso | `aplicacion/cuentas/{GestionCuentas, EliminarCuenta}`, `aplicacion/documentos/GestionDocumentos`, `aplicacion/transacciones/GestionTransacciones` |
| Puertos de lectura | `LecturaCuentas`, `LecturaDocumentos`, implementados por `LecturasJdbc` (SQL directo para el saldo y los conteos) |
| Persistencia | `CuentaRepositorioJdbc`, `DocumentoRepositorioJdbc`, `TransaccionRepositorioJdbc` (JdbcClient, bloqueo optimista con `versionCargada`) |
| Eventos | `TraductorEventosMovimientos` → `movimientos.v1.*` |

**Decisiones de detalle:**
- **Ids tipados.** `CuentaId`, `DocumentoId` y `TransaccionId` en vez de `UUID` sueltos: el compilador
  impide pasar el id de un documento donde se espera el de una cuenta.
- **Operaciones por conjuntos.** Mover o borrar las transacciones de un documento son una sola
  sentencia SQL (`moverDocumentoACuenta`, `eliminarDeDocumento`), no N agregados cargados y guardados.
  El evento del documento es un evento de conjunto que lo cubre. Al moverlas, se incrementa la versión
  de cada transacción.
- **Saldo de la cuenta.** Lo calcula una subconsulta (último con saldo por fecha contable, orden e id);
  no es un atributo del agregado.
- **Casos de uso agrupados.** Los de CRUD simple de un mismo agregado se agrupan en una clase
  `Gestion<X>`. Los que tienen una política propia van en su clase, como `EliminarCuenta` con su
  cascada. Ver la [guía de desarrollo](../proceso/guia-de-desarrollo.md#convenciones).
