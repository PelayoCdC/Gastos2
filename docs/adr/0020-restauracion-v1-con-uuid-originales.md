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
