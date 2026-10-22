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
  - Cabecera `Gastos-Secuencia: <contexto>=<n>[,<contexto>=<n>]` en las respuestas correctas que han
    escrito eventos ([ADR 0011](../adr/0011-leer-lo-que-acabas-de-escribir.md)).
- **Leer lo que acabas de escribir:** el cliente guarda el máximo de cada contexto que ve en
  `Gastos-Secuencia` y lo envía en `Gastos-Esperar` (mismo formato) al consultar en `/api/consultas`.
  consultas espera hasta 3 s a tener esos eventos proyectados; si no llegan, responde igualmente con
  `Gastos-Desfase: true`.

## Recursos por contexto

Se documentan, con ejemplos ejecutables en `http/*.http`, en la fase en que se implementan. Cada servicio
publica además su OpenAPI (F11).

### identidad (`/api/identidad`)

| Método y ruta | Público | Cuerpo | Respuesta | Errores |
|---|---|---|---|---|
| `POST /registros` | sí | `{nombre, email, contrasena}` | 201 `{id, nombre, email}` | 400 `VALIDACION`, `EMAIL_INVALIDO`, `NOMBRE_INVALIDO`, `CONTRASENA_INVALIDA`; 409 `EMAIL_YA_REGISTRADO` |
| `POST /sesiones` | sí | `{email, contrasena}` | 200 `{token, tipo: "Bearer", expiraEnSegundos}` | 401 `CREDENCIALES_INVALIDAS` |
| `GET /usuarios/yo` | no | — | 200 `{id, nombre, email}` | 401 |
| `GET /.well-known/jwks.json` | interno | — | JWKS con la clave pública RSA | — |

**El token** (JWT RS256) lleva estos claims:
- `sub` (id del usuario), `email` y `nombre`;
- `iss=gastos-identidad` y `aud=gastos-api`;
- `iat`, `exp` (24 h) y `jti`.

La cabecera incluye `kid`. Un cliente puede leer `nombre` y `email` del propio token sin hacer otra
petición.

Ejemplos: [`http/identidad.http`](../../http/identidad.http).

### movimientos (`/api/movimientos`)

Cuentas (con saldo), documentos y transacciones manuales: tabla completa en
[dominio/movimientos.md](../dominio/movimientos.md#api-a-través-del-gateway-apimovimientos).
Ejemplos: [`http/movimientos.http`](../../http/movimientos.http).

### clasificacion (`/api/clasificacion`)

Catálogo (categorías, etiquetas, orden de prioridad y definiciones): tabla completa en
[dominio/clasificacion.md](../dominio/clasificacion.md#api-del-catálogo-f5-a-través-del-gateway-apiclasificacion).
Ejemplos: [`http/clasificacion.http`](../../http/clasificacion.http).

### importacion (`/api/importacion`)

Formatos de banco, previsualización e importación de extractos (multipart, hasta 10 MB): tabla completa
en [dominio/importacion.md](../dominio/importacion.md#api-a-través-del-gateway-apiimportacion).
Ejemplos: [`http/importacion.http`](../../http/importacion.http).

### consultas (`/api/consultas`)

Rejilla, detalle y exportación a Excel de las transacciones (filtros por id y orden en lista blanca),
e informes y análisis (`/informes/*`, con importes como números y gastos en positivo): tabla completa en [dominio/consultas.md](../dominio/consultas.md#api-a-través-del-gateway-apiconsultas).
Ejemplos: [`http/consultas.http`](../../http/consultas.http).

### Errores propios del gateway

| Situación | Estado | `codigo` |
|---|---|---|
| Sin token o token inválido (firma, caducidad, emisor o audiencia) | 401 (cuerpo vacío, cabecera `WWW-Authenticate`) | — |
| Ruta interna (`/api/*/internal/**`) | 403 | — |
| Cuerpo de más de 10 MB | 413 | — |
| Servicio de destino inalcanzable | 503 `application/problem+json` | `SERVICIO_NO_DISPONIBLE` |
| Origen no permitido en un preflight CORS | 403 | — |
