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
| identidad | (pendiente) | F1 |
| gateway | (pendiente) | F2 |
| movimientos | (pendiente) | F3–F4 |
| clasificacion | (pendiente) | F5–F6 |
| importacion | (pendiente) | F7 |
| consultas | (pendiente) | F8–F9 |
