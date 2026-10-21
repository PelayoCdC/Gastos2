# Contexto: consultas (lado de lectura CQRS)

**Tipo:** subdominio de soporte, conformista · **Servicio:** `servicios/consultas` · **Estado:** diseñado (se implementa en F8–F10)

## Responsabilidad

Responder todo lo que combina datos de varios contextos: la rejilla y el detalle de transacciones, la
exportación a Excel, los informes, los análisis y la exportación v1. No tiene comandos de negocio: su
modelo se alimenta solo de eventos y se puede reconstruir
([ADR 0010](../adr/0010-consultas-cqrs-reconstruible-por-replay.md)).

## Proyecciones

| Tabla | Origen | Notas |
|---|---|---|
| `transaccion_vista` | movimientos y clasificacion | Fila parcial: parte de movimientos (`registrada`, `version_movimiento`) y parte de clasificación (`categoria_id`, `version_clasificacion`); lápida `eliminada`; `concepto_normalizado` y `clave_concepto` precalculados |
| `transaccion_etiqueta_vista` | clasificacion | Etiquetas de cada transacción, en orden |
| `cuenta_vista`, `documento_vista` | movimientos | |
| `categoria_vista`, `etiqueta_vista` | clasificacion | Con ocurrencias, prioridad y color, para la exportación v1 |
| `formato_vista` | importacion | Para la exportación v1 |
| `proyeccion_posicion` | todos | Última secuencia procesada por usuario y contexto, para `Gastos-Esperar` ([ADR 0011](../adr/0011-leer-lo-que-acabas-de-escribir.md)) |

## Calculadores de dominio (puros)

Son portados del back antiguo con la misma semántica, pero con el `Clock` inyectado y orden cronológico
garantizado.

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

## API (a través del gateway: `/api/consultas`)

- `GET /transacciones`: filtros, ordenación con desempate y paginación.
- `GET /transacciones/{id}`.
- `GET /transacciones/exportacion` (xlsx).
- `GET /informes/{resumen-mensual, evolucion-saldo, gastos-por-categoria, gastos-por-etiqueta, top-conceptos, recurrentes, gasto-diario, gastos-mensuales-por-categoria, bizum}`.
- `GET /exportacion/v1`.
