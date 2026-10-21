# ADR 0013: Nombres únicos por usuario sobre su forma normalizada

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

En el back antiguo:
- Ninguna tabla impedía que un usuario tuviera dos categorías llamadas igual, pero todo el código
  buscaba "la" categoría por nombre. Con duplicados, la búsqueda fallaba con un 500.
- Que "Alimentación" y "alimentacion" fueran el mismo nombre dependía de la collation de MariaDB
  (`utf8mb4_uca1400_ai_ci`), es decir, de un detalle de la base de datos que no aparecía en el código.

## Decisión

- **Normalización:** `Normalizador.normalizarNombre`, en el kernel, quita tildes, pasa a minúsculas,
  colapsa espacios (incluido el espacio duro) y recorta.
- **Unicidad:** cada tabla con nombre de usuario (cuentas, categorías, etiquetas, formatos de banco)
  guarda `nombre_normalizado` en una columna `utf8mb4_bin`, con `UNIQUE(usuario_id, nombre_normalizado)`.
  Así no se depende de la collation.
- **Validación:** el caso de uso comprueba antes de guardar y responde **409 `NOMBRE_DUPLICADO`**. La
  restricción de base de datos es la red de seguridad ante carreras.
- **Buscar o crear por nombre** usa la misma forma normalizada.
- **Al restaurar respaldos con nombres que choquen**, se fusionan: se conserva el primero y se remapean
  los ids de los demás ([0020](0020-restauracion-v1-con-uuid-originales.md)).

## Consecuencias

- ✅ La regla es explícita, está en el dominio y es igual en todos los servicios.
- ✅ Se acaban los 500 por nombres duplicados.
- ⚠️ Dos nombres que antes coexistían ("Café" y "cafe") se fusionan al migrar. El modo SIMULAR de la
  restauración lo informa antes de aplicarlo.
