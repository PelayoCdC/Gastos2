# ADR 0002: La clasificación de cada transacción vive en `clasificacion`

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

Una transacción tiene hechos bancarios (fecha, importe, concepto, saldo) y una clasificación (categoría
y etiquetas). Hay que decidir qué servicio es dueño de la clasificación, porque muchas operaciones
combinan catálogo y asignaciones:

- buscar o crear una categoría o etiqueta por nombre al clasificar;
- la cascada etiqueta → categoría;
- propagar a las similares;
- la aplicación masiva;
- reetiquetar;
- al borrar una categoría o etiqueta, desasignarla de todas las transacciones.

En el back antiguo, este último caso fallaba con un error 500 por una clave foránea.

## Opciones consideradas

1. **Asignaciones en `movimientos`**, con una copia de las reglas del catálogo:
   - la clasificación automática al importar sería síncrona y local;
   - buscar o crear por nombre exigiría una llamada síncrona a `clasificacion` en cada edición;
   - borrar una categoría dejaría durante un tiempo transacciones apuntando a un id inexistente.
2. **Asignaciones en `clasificacion`**, con una copia mínima de cada transacción (concepto, documento)
   alimentada por eventos.

## Decisión

Opción 2. El agregado `ClasificacionTransaccion` (id = id de la transacción) reúne la copia de los datos
de origen y la asignación (categoría, etiquetas y origen de cada una). Así todas las operaciones
anteriores son **locales y atómicas**.
- Si llega un comando para una transacción cuya copia aún no existe (el front la acaba de crear),
  `clasificacion` la consulta a `movimientos` (*read-through*) reenviando el JWT del usuario.
- La clasificación automática al importar es asíncrona: reacciona a `TransaccionRegistrada` con origen
  IMPORTACION.

## Consecuencias

- ✅ El motor de reglas, que es el core domain, queda aislado en un servicio con todo lo que necesita.
  `movimientos` queda como un registro de hechos sencillo.
- ✅ Borrar una categoría la desasigna de transacciones y etiquetas en una sola transacción local.
- ⚠️ Crear una transacción con categoría son dos llamadas desde el front: los hechos a `movimientos` y la
  clasificación a `clasificacion`. Las dos son idempotentes.
- ⚠️ La clasificación automática tarda unos milisegundos tras importar, porque es asíncrona.
- 🔁 Si apareciera una regla que necesite los importes para clasificar (por ejemplo "si el importe es
  mayor que X"), la copia en `clasificacion` tendría que ampliarse.
