# ADR 0019: Spring Cloud Gateway en su variante WebFlux

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (se implementa en F2)

## Contexto

Hace falta una única entrada para el front que:
- enrute `/api/{contexto}/**` a cada servicio;
- centralice CORS, la validación del JWT y el límite de subida;
- oculte los endpoints internos.

Por ella pasan subidas de extractos de hasta 10 MB.

## Opciones consideradas

1. **nginx o Traefik.** Son ligeros, pero validar JWT con JWKS exige módulos o configuración específica,
   y quedarían fuera del ecosistema Java del proyecto.
2. **Spring Cloud Gateway Server WebMVC.** Mismo modelo que los servicios, pero el procesamiento de
   multipart del contenedor de servlets interfiere con el reenvío en streaming de ficheros.
3. **Spring Cloud Gateway Server WebFlux.** Reactivo: reenvía el cuerpo en streaming y es la variante más
   madura.

## Decisión

Opción 3 (`spring-cloud-starter-gateway-server-webflux`, propiedades
`spring.cloud.gateway.server.webflux.*`).
- **Rutas:** las de `/api/*/internal/**` se deniegan antes que cualquier otra. Las demás reescriben
  `/api/{ctx}/(.*)` a `/$1` hacia el servicio correspondiente.
- **Seguridad:**
  - *Resource server* reactivo con el mismo JWKS, emisor y audiencia que los servicios.
  - Rutas públicas: registro y sesión de identidad.
- **CORS** se configura solo aquí. El origen por defecto es `http://localhost:4200`.
- **Trazas:** se propaga `traceparent`.

## Consecuencias

- ✅ La entrada se configura de forma declarativa y conserva las subidas en streaming.
- ⚠️ Es el único servicio reactivo. No tiene lógica de negocio, así que el cambio de modelo no afecta al
  resto.
