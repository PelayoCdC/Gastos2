# 3. Componentes de cada servicio (C4 nivel 3)

Todos los servicios de negocio siguen la misma forma hexagonal
([ADR 0006](../adr/0006-unidad-de-trabajo-en-vez-de-transactional.md)). Este documento muestra la
plantilla común y, por cada servicio, sus puertos y adaptadores concretos. La sección de cada servicio
se añade en la fase en que se construye.

## Plantilla común

```mermaid
flowchart LR
    subgraph entrada [Adaptadores de entrada]
        rest["REST<br/>infraestructura/rest"]
        kafkaIn["Listeners Kafka<br/>infraestructura/kafka"]
        interno["REST interno<br/>infraestructura/interno"]
    end

    subgraph nucleo [Núcleo: Java puro]
        subgraph app [aplicacion]
            cu["@CasoDeUso<br/>(puertos de entrada)"]
            pout["Puertos de salida<br/>UnidadDeTrabajo, PublicadorEventos,<br/>GeneradorIds, Clock, clientes"]
        end
        subgraph dom [dominio]
            ag["Agregados + VOs<br/>eventos de dominio"]
            sd["Servicios de dominio"]
            prep["Puertos de repositorio"]
        end
    end

    subgraph salida [Adaptadores de salida]
        pers["Persistencia<br/>JdbcClient + SQL"]
        outbox["Outbox → relay → Kafka"]
        http["Clientes HTTP<br/>@HttpExchange"]
        tx["TransactionTemplate"]
    end

    rest & kafkaIn & interno --> cu
    cu --> ag & sd
    cu --> prep & pout
    pers -. implementa .-> prep
    outbox -. implementa .-> pout
    http -. implementa .-> pout
    tx -. implementa .-> pout
```

**Regla de dependencia.** Las flechas de código solo apuntan hacia el núcleo. Los adaptadores
**implementan** los puertos (las líneas punteadas) y el núcleo nunca conoce a los adaptadores. Lo verifica
`ReglasArquitectura` en cada build.

## Servicios

| Servicio | Sección | Fase |
|---|---|---|
| identidad | [identidad](#identidad) | F1 ✅ |
| gateway | [gateway](#gateway) | F2 ✅ |
| movimientos | (pendiente) | F3–F4 |
| clasificacion | (pendiente) | F5–F6 |
| importacion | (pendiente) | F7 |
| consultas | (pendiente) | F8–F9 |

## identidad

```mermaid
flowchart LR
    subgraph in [Entrada]
        ctl["IdentidadController<br/>POST /registros, POST /sesiones,<br/>GET /usuarios/yo"]
        jwks["JwksController<br/>GET /.well-known/jwks.json"]
    end
    subgraph app [aplicacion]
        reg["RegistrarUsuario"]
        ses["IniciarSesion"]
        per["ConsultarPerfil"]
        cif(["CifradorContrasenas"])
        emi(["EmisorTokens"])
    end
    subgraph dom [dominio/usuario]
        usu["Usuario + Email, Nombre,<br/>ContrasenaEnClaro, HashContrasena"]
        evt["UsuarioRegistrado"]
        rep(["UsuarioRepositorio"])
    end
    subgraph out [Salida]
        jdbc["UsuarioRepositorioJdbc"]
        bc["CifradorBCrypt"]
        jwt["EmisorTokensJwt + ClaveFirma (RS256)"]
        trad["TraductorEventosIdentidad → outbox"]
    end
    ctl --> reg & ses & per
    reg & ses & per --> usu & rep
    reg --> cif
    ses --> cif & emi
    jdbc -. implementa .-> rep
    bc -. implementa .-> cif
    jwt -. implementa .-> emi
    jwks --> jwt
    usu --> evt --> trad
```

Los puertos de salida (óvalos) los define el núcleo y los implementan los adaptadores. La traducción
del evento de dominio al contrato `identidad.v1.UsuarioRegistrado` ocurre en la infraestructura: el
dominio no conoce el lenguaje publicado.

## gateway

No tiene capas de dominio ni de aplicación: es infraestructura pura y está configurado de forma
declarativa ([ADR 0019](../adr/0019-gateway-webflux.md)).

```mermaid
flowchart LR
    nav["Navegador / front"] --> cors["CORS<br/>(solo orígenes permitidos)"]
    cors --> seg["SeguridadGateway<br/>/api/*/internal/** → 403<br/>registro y sesión públicos<br/>resto: JWT válido (JWKS, iss, aud)"]
    seg --> tam["RequestSize 10 MB → 413"]
    tam --> rutas["Rutas /api/{ctx}/** → /** del servicio<br/>(RewritePath, conserva Authorization)"]
    rutas --> svc["identidad · movimientos · clasificacion · importacion · consultas"]
    rutas -. "servicio inalcanzable" .-> err["ErroresGateway → 503 problem+json"]
```

| Pieza | Responsabilidad |
|---|---|
| `application.yaml` | Rutas por contexto, filtro `RequestSize` por defecto y URL de cada servicio (`gastos.servicios.*`) |
| `SeguridadGateway` | `SecurityWebFilterChain` reactiva, decodificador JWT con JWKS + emisor + audiencia, CORS |
| `ErroresGateway` | Convierte "no se puede conectar con el servicio" en 503 RFC 9457 `SERVICIO_NO_DISPONIBLE` |
