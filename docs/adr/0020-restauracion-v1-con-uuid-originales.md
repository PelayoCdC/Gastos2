# ADR 0020: Respaldo y migración con el formato v1 conservando los UUID

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (se implementa en F10)

## Contexto

- Los datos reales del usuario (unas 2.800 transacciones, catálogo propio y formatos) están en el back
  antiguo, que ya exporta un JSON completo (`ExportacionTotalDto`, versión 1).
- La app móvil restaura ese mismo formato.
- El nuevo sistema tiene que importar esos datos sin pérdidas y seguir generando el formato para el
  móvil.

## Decisión

- **Exportación:** `consultas` ofrece `GET /exportacion/v1` con el mismo formato que el back antiguo,
  generado desde sus proyecciones. Un test de ida y vuelta lo protege.
- **Restauración:** `importacion` ofrece `POST /respaldos?modo=SIMULAR|SUSTITUIR`, que acepta el JSON v1
  (del back antiguo o del móvil).
  - **SIMULAR** informa de conteos, nombres que chocan al normalizarse ([0013](0013-nombres-unicos-normalizados.md))
    y anomalías (fechas nulas, documentos sin cuenta…).
  - **SUSTITUIR** reparte el respaldo a `PUT /internal/respaldos/v1` de cada servicio dueño. Cada
    servicio:
    1. vacía los datos del usuario;
    2. inserta **conservando los UUID originales**;
    3. resuelve sus propias fusiones de nombres remapeando ids internamente;
    4. publica sus eventos.

    Detalles por servicio:
    - `clasificacion` toma la prioridad de la posición en el JSON (× 10, ver [0014](0014-prioridad-explicita.md)).
    - `clasificacion` marca el catálogo como sembrado, para que la siembra por defecto no se aplique
      encima.
    - `movimientos` marca las transacciones con `origen=MIGRACION`, y no se ejecuta la clasificación
      automática.
  - Cada paso es atómico en su servicio, y repetir la restauración da el mismo resultado.
- **Todo pasa por casos de uso y outbox**, nunca por inserciones directas en las bases de datos. Así,
  `consultas` se construye solo con los eventos.

## Consecuencias

- ✅ La migración es repetible y verificable: se comparan los conteos, las sumas por cuenta y mes y las
  asignaciones con `scripts/comparar-respaldos.sh`.
- ✅ La misma funcionalidad sirve de copia de seguridad del nuevo sistema y de puente con el móvil.
- ⚠️ La restauración **sustituye**, no fusiona. Es lo que se necesita para migrar, y lo mismo que hace
  el móvil.
- ⚠️ Los importes migrados arrastran la imprecisión del `float` original ([0012](0012-importe-bigdecimal.md)).

## Notas de implementación (F10, 2026-10-22)

- **SIMULAR lo hace cada dueño.** En vez de que importacion repita las reglas de los demás para
  simular, cada servicio acepta `PUT /internal/respaldos/v1?simular=true`: construye el mismo plan que
  usaría para sustituir (`PlanRestauracion`, puro) y devuelve conteos y anomalías sin escribir nada. Así
  la simulación nunca se desvía de lo que de verdad pasará.
- **importacion traduce y filtra antes de repartir.** El formato v1 (`RespaldoV1`, ahora en
  `compartido/contratos` con su muestra aprobada) trae ids y fechas como texto; `TraductorRespaldoV1`
  los lee y descarta las transacciones sin id, fecha contable o importe **antes** de repartir, para que
  movimientos y clasificacion reciban exactamente el mismo conjunto.
- **Eventos de conjunto para la sustitución:** `MovimientosRestaurados`, `ClasificacionRestaurada` y
  `FormatosRestaurados` van primero, y detrás el alta de cada cosa con su id original. En consultas cada
  uno reinicia **solo su parte** de las filas (lápida en versión 0) porque llegan por topics distintos sin
  orden entre ellos; las altas restauradas (versión 1) las reviven. clasificacion ignora
  `MovimientosRestaurados`: restaura lo suyo en su propia llamada.
- **Saldos a 0 del back antiguo.** El `float` no admitía nulos y un saldo desconocido quedaba a 0. Al
  restaurar, si todas las transacciones de un documento tienen saldo 0, se entiende que el banco no lo
  daba y quedan sin saldo; las altas a mano (sin documento) nunca tienen saldo. Al exportar a v1, un
  saldo desconocido vuelve a escribirse como 0 porque el móvil espera un número.
- **Fusiones por nombre** en cada dueño: cuentas en movimientos; categorías y etiquetas (sumando sus
  ocurrencias) en clasificacion; formatos repetidos se descartan en importacion. Todo queda en las
  anomalías del informe.
- **Comparación:** `scripts/comparar-respaldos.mjs` (Node) compara conteos, ids, sumas por cuenta y mes,
  hechos y asignaciones de cada transacción (por nombre, para que las fusiones no cuenten) y el orden del
  catálogo. Se pensó en un script de shell, pero comparar JSON sin `jq` (que no está en Git Bash) era
  frágil.
- **Un respaldo solo se restaura en la cuenta de la que salió** (o en una cuenta donde sus ids estén
  libres, como en la migración desde el back antiguo). Como los ids se conservan, restaurar el respaldo
  de la cuenta A en la cuenta B chocaría con los datos de A. Cada servicio lo comprueba **antes de
  borrar nada** y responde 409 `RESPALDO_DE_OTRA_CUENTA`; los datos de A nunca corren peligro (las altas
  fallarían por clave primaria, no sobrescriben). Lo destapó el smoke de ida y vuelta, que al principio
  restauraba en un usuario distinto y recibía un 409 "nombre duplicado" engañoso.
- **SUSTITUIR ensaya antes en todos los servicios.** Si alguno rechaza el respaldo, no se ha tocado
  nada en ninguno; así un respaldo de otra cuenta no puede dejar una restauración a medias.
