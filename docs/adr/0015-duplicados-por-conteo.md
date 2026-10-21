# ADR 0015: Detección de duplicados por conteo de claves

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (se implementa en F4)

## Contexto

Los extractos se solapan: el de octubre puede incluir los últimos días de septiembre. Al importar hay
que omitir lo que ya estaba. El back antiguo construía la clave `fecha | importe | concepto normalizado`
y descartaba una fila si su clave ya existía. Para ello añadía al conjunto de claves existentes **las
filas del propio fichero**. Consecuencia: dos movimientos reales idénticos en el mismo extracto (dos
cafés iguales el mismo día) se quedaban en uno.

## Opciones consideradas

1. **Mantener el conjunto de claves.** Se pierden movimientos legítimos.
2. **Comparar por conteo** (multiconjunto).

## Decisión

Opción 2, en el servicio de dominio puro `DetectorDuplicados` de `movimientos`:
1. Para cada clave `k`, se cuenta cuántas transacciones existentes la tienen en el ámbito: la cuenta
   destino más las transacciones sin cuenta, como antes.
2. En el fichero, las primeras `existentes(k)` filas con esa clave se marcan como duplicadas y el resto
   se importa.

Así:
- La primera importación de un extracto con dos filas idénticas importa **las dos**.
- Reimportar el mismo extracto marca **las dos** como duplicadas.
- Un extracto que se solapa con otro solo importa lo nuevo.

Para evitar carreras entre dos importaciones simultáneas en la misma cuenta, `RegistrarExtracto` toma
un bloqueo sobre la fila de la cuenta (`SELECT … FOR UPDATE`).

La clave de una transacción importada se fija al importarla y no cambia aunque se edite el concepto. Las
manuales recalculan su clave al editarse, como antes.

## Consecuencias

- ✅ No se pierden movimientos legítimos y la reimportación sigue siendo idempotente.
- ⚠️ Cambia el comportamiento respecto al back antiguo y a la app móvil, que conservan el conjunto. Está
  documentado en la comparativa.
- ⚠️ Si el banco cambia el texto del concepto entre dos extractos, la fila no se reconoce como
  duplicada. Ya pasaba antes; no tiene solución sin un identificador del banco.
