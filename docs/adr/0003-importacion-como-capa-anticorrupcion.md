# ADR 0003: `importacion` es un servicio aparte y actúa de capa anticorrupción

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

Cada banco exporta sus extractos a su manera: columnas, fila de inicio, formatos de fecha, separadores
decimales, CSV en distintas codificaciones. Leer Excel exige Apache POI, una librería pesada, con
consumo alto de memoria y con historial de CVE. Además, la restauración de respaldos (migración desde el
back antiguo o desde el móvil) también es "meter datos de fuera".

## Opciones consideradas

1. **Dentro de `movimientos`.** Menos piezas y sin llamada de red al importar. A cambio, POI y los
   formatos de cada banco entrarían en el servicio que guarda los hechos.
2. **Servicio propio fino.** Traduce el lenguaje de cada banco (hojas, celdas, columnas) al modelo limpio
   de `movimientos` (`LineaExtracto`) y le entrega las líneas con una llamada síncrona.

## Decisión

Opción 2. `importacion` es la **capa anticorrupción** entre los bancos y el dominio:
- es dueña de los formatos de banco (`FormatoBanco`);
- interpreta los ficheros (`InterpreteExtracto`, una función pura);
- llama a `POST /internal/extractos` de `movimientos`, que deduplica y registra.

Detalles de esa llamada:
- `importacion` genera el `extractoId` antes de llamar, y `movimientos` lo usa como clave de
  idempotencia: un reintento devuelve el mismo resultado.
- Solo se reintenta ante errores de conexión o un 503.

También orquesta la restauración de respaldos v1 ([0020](0020-restauracion-v1-con-uuid-originales.md)).

## Consecuencias

- ✅ Ningún otro servicio sabe de Excel, CSV ni bancos. Un banco nuevo solo toca `importacion`.
- ✅ Es el único punto con comunicación síncrona cliente/proveedor; sirve como ejemplo de reintentos,
  timeouts y propagación de errores (un 404 de `movimientos` se devuelve tal cual).
- ⚠️ Una importación depende de que `movimientos` esté disponible: si no lo está, la importación falla
  con 503 y el usuario reintenta.
- 🔁 Si sobran servicios, es el primero que se fusionaría con `movimientos`.

## Notas de implementación (F7, 2026-10-22)

- **Solo se reintentan los errores de conexión** (`ResourceAccessException`, dos reintentos a 200 ms),
  no los 503. movimientos no depende de otros servicios y nunca responde 503 por sí mismo; un 5xx suyo
  es un fallo que no se arregla repitiendo, así que se traduce directamente a 503
  `SERVICIO_NO_DISPONIBLE` para el cliente.
- **Los errores de movimientos conservan su `codigo`.** 404 → `RecursoNoEncontrado`, 400 →
  `ExcepcionDominio` y 409 → `Conflicto`, con el `codigo` y el `detail` del ProblemDetail original. El
  cliente no sabe (ni necesita saber) que hubo dos saltos.
- **importacion no comprueba la cuenta.** Es un dato de movimientos y se comprueba allí con el mismo JWT;
  duplicar la comprobación exigiría otra llamada o una copia de las cuentas.
- **Ni ficheros ni movimientos se guardan en importacion.** La única escritura de la importación es la
  transacción de movimientos, por eso el caso de uso no abre una unidad de trabajo para importar.
