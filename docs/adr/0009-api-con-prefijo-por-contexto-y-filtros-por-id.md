# ADR 0009: API rediseñada: prefijo por contexto, filtros por id, RFC 9457

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

La API antigua tenía varias particularidades:
- búsquedas `POST /x/search` con el criterio en el cuerpo, devolviendo el `PageImpl` de Spring, que
  filtraba campos internos;
- filtros de informes por **nombre**, con un parche (`getParameterValues`) porque los nombres podían
  llevar comas;
- números y fechas como texto en formatos distintos;
- errores con el formato por defecto de Boot;
- 500 ante entradas malformadas.

El usuario decidió **rediseñar la API**: el front se adaptará después.

## Decisión

- **Una sola entrada** (gateway) con **prefijo por contexto**: `/api/identidad`, `/api/movimientos`,
  `/api/clasificacion`, `/api/importacion` y `/api/consultas`. El gateway reescribe a la ruta interna y
  **deniega** `/api/*/internal/**`.
  - El prefijo usa los nombres del mapa de contextos, que forman parte del lenguaje.
  - El enrutado es trivial.
- **Qué servicio responde a cada petición:**
  - Los comandos van al servicio dueño del dato.
  - Las listas de un solo contexto, para las pantallas de edición, también: cuentas con saldo,
    documentos, catálogo y formatos.
  - Todo lo que combina contextos se lee de `consultas`: rejilla, detalle, xlsx, informes y exportación.
- **Filtros por id:** las categorías y etiquetas se filtran por `categoriaId` y `etiquetaId` (parámetros
  repetibles), no por nombre. Los informes devuelven los ids para que la navegación desde los gráficos
  funcione.
- **Formatos:**
  - Errores RFC 9457 (`application/problem+json`) con un `codigo` estable y `errores[{campo, mensaje}]`.
  - Entrada malformada → 400, recurso ajeno o inexistente → 404, conflicto → 409, fichero grande → 413,
    tipo no soportado → 415.
  - Paginación `Pagina<T>` (`contenido`, `pagina`, `tamano`, `totalElementos`, `totalPaginas`).
  - Dinero como número JSON con 2 decimales; fechas ISO `yyyy-MM-dd`.
- **Crear o editar una transacción** son dos recursos: los hechos en `movimientos` y la clasificación en
  `clasificacion` ([0002](0002-asignaciones-en-clasificacion.md)).

## Consecuencias

- ✅ La API es coherente y predecible, y cada error se puede tratar por su `codigo`.
- ✅ Se acaba el problema de los nombres con comas.
- ⚠️ El front actual no funciona con la nueva API hasta adaptarlo (queda fuera de este proyecto). La guía
  de la API y el OpenAPI de cada servicio son la referencia para hacerlo.
