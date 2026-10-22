# Contexto: consultas (lado de lectura CQRS)

**Tipo:** subdominio de soporte, conformista · **Servicio:** `servicios/consultas` (puerto 8085) ·
**Estado:** proyecciones, rejilla, detalle, Excel y `Gastos-Esperar` implementados en F8; informes,
análisis y reconstrucción en F9; exportación v1 en F10

## Responsabilidad

Responder todo lo que combina datos de varios contextos: la rejilla y el detalle de transacciones, la
exportación a Excel, los informes, los análisis y la exportación v1. No tiene comandos de negocio: su
modelo se alimenta solo de eventos y se puede reconstruir
([ADR 0010](../adr/0010-consultas-cqrs-reconstruible-por-replay.md)).

**Por qué es "conformista".** consultas no traduce los eventos a un modelo propio: sus tablas reflejan
el lenguaje publicado por movimientos y clasificacion, y su capa de aplicación trabaja directamente con
los contratos. Lo único que añade es lo que necesita para buscar rápido (el concepto normalizado y su
clave sin fechas, calculados una vez al proyectar con las funciones del kernel).

## Proyecciones

| Tabla | Origen | Notas |
|---|---|---|
| `transaccion_vista` | movimientos y clasificacion | Fila parcial: parte de movimientos (`registrada`, `version_movimientos`, hechos) y parte de clasificación (`categoria_id`, `version_clasificacion`); lápida `eliminada`; `concepto_normalizado` y `clave_concepto` precalculados |
| `transaccion_etiqueta_vista` | clasificacion | Etiquetas de cada transacción, en orden |
| `cuenta_vista`, `documento_vista` | movimientos | Con lápida |
| `categoria_vista`, `etiqueta_vista` | clasificacion | Con ocurrencias (JSON), prioridad y color, para la exportación v1 |
| `formato_vista` | importacion | Para la exportación v1 (F10) |
| `proyeccion_posicion` | todos | Última secuencia procesada por usuario y contexto, para `Gastos-Esperar` ([ADR 0011](../adr/0011-leer-lo-que-acabas-de-escribir.md)) |

### Reglas de la proyección (`ProyectarEventos` + `ProyeccionJdbc`)

Todas las escrituras siguen el mismo patrón, y por eso reprocesar un lote o el topic entero no
estropea nada:

1. **Esqueleto:** `INSERT IGNORE` de la fila con solo id y usuario. Lo hace el primer evento que llegue
   de esa fila, venga del contexto que venga.
2. **Actualización protegida por versión:** `UPDATE ... WHERE version_x < :version`. Un evento repetido
   o más antiguo que lo proyectado no actualiza nada y se ignora entero, incluidos sus efectos de
   conjunto.

| Situación | Cómo se resuelve |
|---|---|
| La clasificación llega antes que el registro (topics distintos, sin orden entre ellos) | La fila nace en esqueleto con la clasificación; la rejilla solo la muestra cuando `registrada` |
| Entrega repetida o atrasada (reintentos de Kafka, reproducción) | La versión del agregado la descarta |
| Clasificación tardía de una transacción ya borrada | La lápida `eliminada` se queda: el evento actualiza la parte de clasificación de una fila oculta |
| `DocumentoReasignado` | Evento de conjunto: `UPDATE transaccion_vista SET cuenta_id ... WHERE documento_id = ?` |
| `DocumentoEliminado` | Lápida en el documento y en todas sus transacciones |
| `CategoriaEliminada` | Lápida; sus transacciones y etiquetas se quedan sin categoría |
| `EtiquetaEliminada` | Lápida; se quita de todas las transacciones |
| `CuentaEliminada` | Lápida (si tenía movimientos, antes han llegado sus `DocumentoEliminado` y `TransaccionEliminada`) |

**Por lotes.** El consumidor recibe hasta 500 mensajes de `movimientos.eventos` y
`clasificacion.eventos` y los aplica en una sola transacción. Medido en el smoke de F8 (todo en
Docker en el portátil): un extracto de 150 filas está en la rejilla 224 ms después de pedirla con
`Gastos-Esperar`, y uno de 2000 filas en 2,6 s, cerca del tope de 3 s (ver
[riesgos](../arquitectura/07-riesgos-y-deuda.md)). Si el lote falla, se repite
mensaje a mensaje para aislar el culpable, que se reintenta y, si sigue fallando, va al DLT
(`consultas.dlt`) sin bloquear la partición.

### Reconstrucción

Las proyecciones son desechables: se pueden tirar y rehacer releyendo los topics, que tienen retención
infinita ([ADR 0010](../adr/0010-consultas-cqrs-reconstruible-por-replay.md)).

- **Versión de la proyección.** `gastos.consultas.proyeccion.version` (en compose,
  `GASTOS_CONSULTAS_PROYECCION_VERSION`) da nombre al grupo de consumidores (`consultas-v<version>`) y
  se guarda en la tabla `proyeccion_estado`.
- **Al arrancar** (`PreparacionProyeccion`, después de Flyway y antes de que empiecen los consumidores),
  `ReconstruirProyeccion` compara la versión configurada con la guardada. Si son distintas, o no hay
  ninguna guardada, vacía las tablas de proyección y las posiciones de lectura (con `DELETE` dentro de
  una transacción: `TRUNCATE` confirmaría a medias).
- **Releer desde el principio.** Tras vaciar, el consumidor se reposiciona al principio de cada
  partición al recibirlas (`ConsumerSeekAware`). No basta con que el grupo sea nuevo: si alguien vuelve
  a una versión anterior, ese grupo ya tiene sus posiciones al final del topic y no releería nada.
- **Saber cuándo ha terminado.** `GET /actuator/health/proyeccion` muestra la versión, el grupo y el
  **retraso** (eventos publicados que aún no se han proyectado). `scripts/reconstruir-consultas.sh`
  sube la versión, recrea el contenedor y espera a que el retraso sea 0
  ([operación](../proceso/operacion.md#reconstruir-consultas)).
- Mientras se reconstruye, las consultas responden con lo que lleve proyectado y `Gastos-Esperar` espera
  igual que siempre, porque las posiciones de lectura también se rehacen.

## Leer lo que acabas de escribir

([ADR 0011](../adr/0011-leer-lo-que-acabas-de-escribir.md))

- **Productores:** el filtro `FiltroSecuenciaPublicada` de `compartido/mensajeria` añade
  `Gastos-Secuencia: <contexto>=<n>` a las respuestas correctas de los comandos, con el id de outbox del
  último evento escrito. importacion reenvía la de movimientos, que es quien escribe los eventos de una
  importación.
- **consultas:** el `InterceptorEsperaConsistencia` lee `Gastos-Esperar`, ignora los contextos que no
  proyecta (por ejemplo, `identidad`) y espera a que `proyeccion_posicion` alcance las secuencias,
  comprobando cada 50 ms durante 3 s como mucho. Si no llegan, responde igualmente con
  `Gastos-Desfase: true`. Una cabecera mal formada da 400 `CABECERA_INVALIDA`.
- **Por qué basta la posición por usuario:** los eventos de un usuario van todos a la misma partición
  (la clave es el usuario) y se publican en orden de secuencia, así que haber procesado la secuencia `n`
  de un usuario implica haber procesado todas las anteriores de ese usuario en ese contexto.

## Rejilla y filtros

Las reglas son las del back antiguo, que el usuario ya conoce (`FiltroTransacciones`):

- **Concepto:** contiene el texto, sin distinguir mayúsculas ni tildes. `%` y `_` son literales.
- **Fechas** (`desde`, `hasta`, sobre la fecha contable) e **importes** (`importeMin`, `importeMax`):
  ambos extremos incluidos. Un rango al revés da 400 `FILTRO_INVALIDO`.
- **Categorías:** "tiene una de `categoriaId`" **o**, con `sinCategoria`, "no tiene categoría".
  **Etiquetas**, igual. Los dos grupos se combinan con **y**.
- **Cuenta y documento** por id.
- **Orden** (`Ordenacion`) por `fechaContable` (por defecto, descendente), `fechaValor`, `importe`,
  `concepto`, `cuenta` o `categoria`, siempre con desempate por `orden` (la fila del extracto) y por
  id. Un campo fuera de la lista da 400 `ORDEN_INVALIDO`.

## Calculadores de dominio (puros)

`dominio/informes`: `Informes`, `DetectorRecurrentes` y `AgregadorBizum`, sobre los `Apunte`s del
usuario (`LecturaApuntes` los carga con dos consultas: transacciones y etiquetas). Son portados del back
antiguo con la misma semántica y estas diferencias:

- dinero `BigDecimal` exacto (antes `double` y textos con dos decimales);
- "hoy" inyectado con el `Clock` (antes `LocalDate.now()`);
- meses ordenados como números (el antiguo ordenaba "10" antes que "9" en la composición mensual);
- saldos desconocidos fuera de la evolución del saldo (antes contaban como 0) y cuentas por id;
- filtros por id; el grupo "Sin categoría" llega con `categoria: null`;
- periodicidad como enumerado (`SEMANAL`, `QUINCENAL`, `MENSUAL`, `TRIMESTRAL`, `ANUAL`).

Por qué en memoria y no en SQL: un usuario tiene unos pocos miles de movimientos, así que cargarlos
cuesta milisegundos, y con funciones puras toda la semántica se prueba sin base de datos (los casos de
`dominio.spec.ts` del móvil están portados en `InformesTest`).

- **Resumen mensual:**
  - `gastos` = suma de `-importe` de los importes negativos.
  - `ingresos` = suma de los positivos.
  - `balance` = `ingresos - gastos`.
  - Se puede filtrar por categorías **o** etiquetas; si llegan ambas, mandan las categorías.
- **Evolución del saldo:** por cuenta, el último saldo de cada día por `orden`. Se ignoran los saldos
  nulos.
- **Gastos por categoría:**
  - Hay un grupo "Sin categoría" con `categoriaId` nulo.
  - Por etiqueta, una transacción cuenta en cada una de sus etiquetas; el filtro opcional usa la
    categoría **de la etiqueta**.
- **Top de conceptos:** agrupa por `ClaveConcepto` (total, número y media).
- **Gastos recurrentes (`DetectorRecurrentes`):**
  - Al menos 3 apariciones.
  - Mediana de los intervalos:

    | Intervalo (días) | Periodicidad |
    |---|---|
    | 5–9 | semanal |
    | 12–18 | quincenal |
    | 21–45 | mensual |
    | 75–105 | trimestral |
    | 330–400 | anual |

  - Importe estable: (p75 − p25) / mediana ≤ 0,3.
  - Activo si la última aparición es posterior a hoy − 2 × intervalo.
  - Coste anual = mediana × 365 / intervalo.
- **Gasto diario:** suma de gastos por día.
- **Gastos mensuales por categoría:** con su color.
- **Bizum (`AgregadorBizum`):**
  - Detecta por "bizum" en el concepto o por la etiqueta Bizum.
  - El contacto es el texto tras el primer `:`, o el concepto entero.
  - Calcula enviado, recibido, neto y veces.

## API (a través del gateway, `/api/consultas`)

Todas las consultas aceptan `Gastos-Esperar` y pueden responder `Gastos-Desfase: true`.

| Método y ruta | Parámetros | Respuesta | Errores |
|---|---|---|---|
| `GET /transacciones` | `concepto`, `desde`, `hasta`, `importeMin`, `importeMax`, `cuentaId`, `documentoId`, `categoriaId`*, `sinCategoria`, `etiquetaId`*, `sinEtiqueta`, `orden`, `direccion` (`ASC`/`DESC`), `pagina`, `tamano` (≤ 500) | 200 `Pagina` de `{id, fechaContable, fechaValor, concepto, nota, importe, saldo, orden, cuenta{id,nombre}, documento{id,nombre}, categoria{id,nombre,color}, etiquetas[{id,nombre}]}` | 400 `FILTRO_INVALIDO`, `ORDEN_INVALIDO`, `PARAMETRO_INVALIDO`, `PAGINACION_INVALIDA`, `CABECERA_INVALIDA` |
| `GET /transacciones/{id}` | — | 200 la misma fila | 404 (también si es de otro usuario o está borrada) |
| `GET /transacciones/exportacion` | los filtros y el orden de la rejilla, sin paginar | 200 `.xlsx` (`Content-Disposition: attachment; filename="transacciones-<fecha>.xlsx"`) | los de la rejilla; 400 `EXPORTACION_DEMASIADO_GRANDE` por encima de 50 000 filas |
| `GET /informes/resumen-mensual` | `desde`, `hasta`, `categoriaId`*, `etiquetaId`* | 200 `[{anio, mes, gastos, ingresos, balance}]` | 400 `FILTRO_INVALIDO` |
| `GET /informes/evolucion-saldo` | `desde`, `hasta` | 200 `[{cuenta{id,nombre}, puntos[{fecha, saldo}]}]` | |
| `GET /informes/gastos-por-categoria` | `desde`, `hasta` | 200 `[{categoria{id,nombre,color} \| null, total, numeroTransacciones}]`, de mayor a menor | |
| `GET /informes/gastos-por-etiqueta` | `desde`, `hasta`, `categoriaId`* (categoría **de la etiqueta**) | 200 `[{etiqueta{id,nombre}, total, numeroTransacciones}]` | |
| `GET /informes/gasto-diario` | `desde`, `hasta`, `categoriaId`*, `etiquetaId`* | 200 `[{fecha, gasto}]` | |
| `GET /informes/gastos-mensuales-por-categoria` | `desde`, `hasta` | 200 `[{anio, mes, categoria \| null, total}]` | |
| `GET /informes/top-conceptos` | `desde`, `hasta`, `categoriaId`*, `etiquetaId`*, `limite` (15; 1–100) | 200 `[{concepto, total, numero, media}]` | 400 `LIMITE_INVALIDO` |
| `GET /informes/recurrentes` | `categoriaId`*, `etiquetaId`* (todo el histórico) | 200 `[{concepto, periodicidad, cadaDias, importeMedio, costeAnual, ultimaFecha, activo, veces}]` (30 como mucho) | |
| `GET /informes/bizum` | `desde`, `hasta`, `categoriaId`*, `etiquetaId`* | 200 `[{contacto, enviado, recibido, neto, veces}]` (20 como mucho) | |
| `GET /exportacion/v1` (F10) | | | |

En los informes con `categoriaId` y `etiquetaId` a la vez, mandan las categorías (como en el antiguo).
Los gastos se devuelven en positivo.

\* Repetible (`categoriaId=a&categoriaId=b`) o separado por comas.

**El Excel** conserva las 7 columnas del back antiguo en el mismo orden (Fecha, Concepto, Cuenta,
Categoria, Etiquetas, Importe, Saldo) y añade al final Fecha valor, Nota y Documento. Las fechas son
fechas de Excel (antes, texto ISO), los importes números con dos decimales y un saldo desconocido queda
vacío (antes, 0).

Ejemplos: [`http/consultas.http`](../../http/consultas.http).
