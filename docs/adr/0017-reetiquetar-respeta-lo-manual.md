# ADR 0017: Reetiquetar respeta las asignaciones manuales

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (se implementa en F6)

## Contexto

En el back antiguo, "reetiquetar" borraba la categoría y **todas** las etiquetas de **todas** las
transacciones y volvía a aplicar las reglas. Servía para que un cambio en las ocurrencias se reflejara en
todo el histórico, pero destruía el trabajo manual: las categorías puestas a mano, las propagadas y las
asignadas en bloque.

## Decisión

- **Cada asignación guarda su origen:** AUTOMATICA, CASCADA, MANUAL, PROPAGACION, MASIVA o MIGRACION.
  - La categoría lo guarda en `origen_categoria`.
  - Cada etiqueta lo guarda en su fila de `clasificacion_etiqueta`.
- **`POST /automatica` con `modo=REINICIAR`:**
  1. Quita solo las asignaciones con origen AUTOMATICA o CASCADA.
  2. Vuelve a aplicar las reglas: etiquetas, después cascada y después categorías. Ninguna pisa una
     asignación conservada.
- **Con `forzar=true`** reinicia todo, como el comportamiento antiguo.
- **Los datos migrados (MIGRACION) cuentan como manuales.** No se sabe cómo se asignaron en el back
  antiguo, y en la duda se conservan.

## Consecuencias

- ✅ Ajustar las reglas y reaplicarlas ya no destruye el trabajo manual.
- ⚠️ Hay más estado por asignación (el origen) y más casuística que probar. Los tests del motor cubren
  cada combinación.
- ⚠️ Tras migrar, reetiquetar sin `forzar` no cambia nada de lo migrado: para empezar de cero con las
  reglas nuevas hay que forzar.
