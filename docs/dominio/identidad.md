# Contexto: identidad

**Tipo:** subdominio genérico · **Servicio:** `servicios/identidad` · **Estado:** diseñado (se implementa en F1)

## Responsabilidad

Registrar usuarios, autenticarlos y emitir el JWT con el que se identifican ante el resto del sistema.
Publica la clave pública (JWKS) para que los demás validen los tokens sin compartir secretos
([ADR 0008](../adr/0008-jwt-rs256-con-jwks.md)).

## Modelo

### Agregado `Usuario`
| Elemento | Tipo | Invariantes |
|---|---|---|
| `id` | `UsuarioId` (kernel) | Asignado al registrarse (UUIDv7) |
| `email` | VO `Email` | Se recorta y pasa a minúsculas canónicas; formato válido; ≤ 254 caracteres; **único** en el sistema |
| `nombre` | VO `Nombre` | Obligatorio, 1–100 caracteres (antes podía ser nulo) |
| `hashContrasena` | VO `HashContrasena` | Opaco para el dominio; la contraseña en claro nunca se guarda |

**Política de contraseña:** al menos 8 caracteres. Antes solo se exigía que no estuviera vacía.

### Puertos
- `UsuarioRepositorio` (persistencia).
- `CifradorContrasenas`: adaptador BCrypt. Si el email no existe, calcula igualmente un hash ficticio
  para que el tiempo de respuesta no revele si el usuario existe.
- `EmisorTokens`: adaptador RS256.
- `PublicadorEventos`, `UnidadDeTrabajo`, `Clock`.

## Casos de uso

| Caso de uso | Entrada → salida | Errores |
|---|---|---|
| `RegistrarUsuario` | nombre, email, contraseña → `{id, nombre, email}` | 400 (validación), 409 `EMAIL_YA_REGISTRADO` |
| `IniciarSesion` | email, contraseña → `{token, tipo: "Bearer", expiraEnSegundos}` | 401 `CREDENCIALES_INVALIDAS` (igual si el email no existe) |
| `ConsultarPerfil` | usuario del token → `{id, nombre, email}` | 404 si el usuario ya no existe |

## Eventos publicados

- `UsuarioRegistrado{usuarioId, email, nombre}`: `clasificacion` reacciona sembrando el catálogo inicial.

## API (a través del gateway: `/api/identidad`)

- `POST /registros` (público).
- `POST /sesiones` (público).
- `GET /usuarios/yo`.
- `GET /.well-known/jwks.json` (interno; lo consultan el gateway y los servicios).
