# Guía de la API

Convenciones generales de la API pública (a través del gateway). El porqué está en el
[ADR 0009](../adr/0009-api-con-prefijo-por-contexto-y-filtros-por-id.md).

## Convenciones

- **Rutas:** base `http://localhost:8090/api/{contexto}/...`, con contexto `identidad`, `movimientos`,
  `clasificacion`, `importacion` o `consultas`.
- **Autenticación:** `Authorization: Bearer <token>` en todo, salvo `POST /api/identidad/registros` y
  `POST /api/identidad/sesiones`.
- **Errores:** `application/problem+json` (RFC 9457) con `codigo` estable. Ver
  [conceptos transversales](../arquitectura/05-conceptos-transversales.md#56-errores-rfc-9457).
- **Paginación:** `?pagina=0&tamano=50` (máximo 500). La respuesta es
  `{contenido, pagina, tamano, totalElementos, totalPaginas}`.
- **Formatos:**
  - Dinero como número JSON con dos decimales (`-12.30`).
  - Fechas `yyyy-MM-dd`.
  - Ids en formato UUID.
- **Respuestas de los comandos:**
  - Crear devuelve `201 Created` con `Location`.
  - Borrar devuelve `204 No Content`.
  - Cabecera `Gastos-Secuencia` ([ADR 0011](../adr/0011-leer-lo-que-acabas-de-escribir.md)).

## Recursos por contexto

Se documentan, con ejemplos ejecutables en `http/*.http`, en la fase en que se implementan. Cada servicio
publica además su OpenAPI (F11).
