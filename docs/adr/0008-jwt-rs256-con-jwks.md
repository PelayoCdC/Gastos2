# ADR 0008: JWT RS256 emitido por identidad y validado con JWKS

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

El back antiguo firmaba y validaba los JWT con un secreto compartido (HS256). Con varios servicios, eso
obligaría a repartir el secreto a todos, y cualquiera de ellos podría **emitir** tokens válidos. Además:
- no se validaban el emisor ni la audiencia;
- no había rotación de claves.

## Opciones consideradas

1. **HS256 con secreto compartido** en todos los servicios.
2. **Servidor de autorización externo** (Keycloak, Spring Authorization Server). Es lo más estándar,
   pero añade un componente pesado y saca la identidad del modelo.
3. **RS256 con clave privada solo en identidad** y la pública publicada como JWKS.

## Decisión

Opción 3.
- **Emisión:** `identidad` firma con su clave privada RSA y publica
  `GET /.well-known/jwks.json`.
- **Claims:**
  - `iss=gastos-identidad` y `aud=gastos-api`;
  - `sub` = UsuarioId, `email`, `nombre`;
  - `exp` a 24 h y `jti`.
  - La cabecera lleva `kid` para permitir la rotación de claves.
- **Validación:** el gateway y todos los servicios son *resource servers* que validan firma, `exp`,
  emisor y audiencia con el JWKS. La configuración común está en `compartido/web`.
- **Llamadas entre servicios:** reenvían el JWT del usuario, sin cuentas de servicio. La propiedad de los
  datos se comprueba igual en todas partes.
- **Clave en desarrollo:**
  - `scripts/generar-claves-dev.sh` genera `infra/claves/jwt-privada.pem`, que está en `.gitignore` y se
    monta solo en identidad.
  - Si falta, identidad genera una clave efímera y avisa en el log (los tokens dejan de valer al
    reiniciar).

## Consecuencias

- ✅ Solo identidad puede emitir tokens. Comprometer otro servicio no permite falsificar identidades.
- ✅ La rotación de claves es posible publicando varias en el JWKS.
- ✅ Los servicios no dependen de que identidad esté arriba para validar: Nimbus cachea las claves.
- ⚠️ No hay *refresh token* ni revocación (igual que antes): un token robado vale hasta que caduca.
  Para una aplicación personal se acepta.
