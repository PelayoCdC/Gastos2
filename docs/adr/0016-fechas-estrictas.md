# ADR 0016: Fechas estrictas al interpretar extractos

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (se implementa en F7)

## Contexto

El back antiguo interpretaba las fechas de texto con el resolver por defecto de Java (`SMART`), que
"corrige" en silencio las fechas imposibles: `31/02/2024` se convertía en el 29 de febrero sin avisar.
Una fecha así indica casi siempre que el formato está mal configurado (por ejemplo, día y mes
cambiados), y aceptarla esconde el error. La app móvil ya era estricta.

## Decisión

- **`FormatoFecha`** (value object de `importacion`) compila el patrón con `ResolverStyle.STRICT`. Antes
  sustituye `y` por `u`, porque en modo estricto `yyyy` exige la era.
- **Una fecha imposible es un error de fila** (`"Fila N: fecha con formato incorrecto: '31/02/2024'"`):
  la fila no se importa y se informa en la respuesta.
- **Las celdas de Excel con tipo fecha** se leen directamente, sin patrón.

## Consecuencias

- ✅ Los errores de configuración del formato salen a la luz en la primera importación.
- ✅ El back se comporta igual que la app móvil.
- ⚠️ Un extracto con fechas realmente imposibles, que sería un error del banco, ya no se importa
  entero: esas filas se informan como errores.
