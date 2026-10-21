# ADR 0014: Prioridad explícita en categorías y etiquetas

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

Dos reglas de clasificación dependen de un orden:
- la categoría por ocurrencias, donde **gana la primera** que coincide;
- la cascada, que toma la categoría de **la primera etiqueta** que tenga una.

En el back antiguo ese orden era el de la base de datos sin `ORDER BY`, es decir, el orden de los UUID
aleatorios. Además, la siembra inicial usaba un `Map.ofEntries`, cuyo orden de iteración cambia en cada
ejecución. Con el catálogo por defecto hay casos reales afectados: "PAYPAL *SPOTIFY" casa con Compras y
con Ocio, y "AMAZON PRIME VIDEO" también tiene varias candidatas.

## Decisión

- Categorías y etiquetas tienen un campo **`prioridad`** (entero; menor = antes).
- **Las reglas usan siempre ese orden:**
  - la primera categoría por prioridad gana;
  - la cascada aplica la categoría de la etiqueta asignada de mayor prioridad que tenga una;
  - a igual prioridad, se desempata por nombre normalizado, para que el resultado sea determinista.
- **Asignación de la prioridad:**
  - Las nuevas se añaden al final (`max + 1`).
  - El usuario reordena con `PUT /categorias/orden` (y lo mismo para etiquetas), enviando la permutación
    completa de sus ids.
- **Catálogo inicial:** `catalogo-inicial.json` lista las entradas en un orden pensado, y ese orden fija
  la prioridad.
- **Migración:** la prioridad se toma de la posición en el JSON v1 (× 10). Esa posición es el orden en
  que el back antiguo leía las filas, así que se conserva su comportamiento efectivo.

## Consecuencias

- ✅ La clasificación es determinista, y el usuario controla qué regla gana.
- ✅ Los tests pueden afirmar el resultado exacto.
- ⚠️ Hay un concepto nuevo en la interfaz: reordenar. Hasta que el front lo soporte, el orden por defecto
  es el de creación.
