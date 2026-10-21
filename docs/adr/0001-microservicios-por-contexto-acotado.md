# ADR 0001: Un microservicio por contexto acotado, con su propia base de datos

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (antes de F0)

## Contexto

El back antiguo es un monolito con 9 módulos por entidad (`transacciones`, `categorias`, `cuentas`…)
muy acoplados:
- `Transaccion` tiene `@ManyToOne` hacia entidades de cuatro módulos;
- hay ciclos entre paquetes;
- la importación crea documento, transacciones y clasificación en una sola transacción de base de datos.

El objetivo del proyecto es aplicar microservicios y DDD (ver
[objetivos](../arquitectura/01-objetivos-y-restricciones.md)). Partir por entidad reproduciría el
acoplamiento con red de por medio. Las fronteras tienen que estar donde el lenguaje y las reglas
cambian.

## Opciones consideradas

1. **Un servicio por módulo actual (por entidad).** Es simple de mapear, pero cada operación de negocio
   cruzaría varios servicios (clasificar una transacción tocaría transacciones, categorías y etiquetas).
2. **Monolito modular** (un desplegable con módulos aislados). Es lo más sensato para una aplicación
   personal, pero no cumple el objetivo de aprender y aplicar microservicios.
3. **Un servicio por contexto acotado**, identificando los contextos por su lenguaje y sus invariantes:
   identidad, movimientos, clasificacion, importacion y consultas.

## Decisión

Opción 3. Seis desplegables: los cinco contextos más el gateway. Cada servicio tiene **su propia base de
datos y su propio usuario de base de datos**, y nadie lee la base de otro.
- Los datos ajenos llegan por eventos (copias de solo lectura) o por API interna.
- Las referencias entre contextos son **ids**, nunca relaciones de base de datos.

## Consecuencias

- ✅ Cada contexto evoluciona su modelo sin romper a los demás. Los contratos entre ellos (eventos v1,
  API interna) son explícitos y versionados.
- ✅ Las operaciones que combinan catálogo y clasificación son atómicas dentro de `clasificacion`
  (ver [0002](0002-asignaciones-en-clasificacion.md)).
- ⚠️ Hay consistencia eventual entre servicios: la rejilla de consultas puede ir unos milisegundos por
  detrás. Se mitiga con [0011](0011-leer-lo-que-acabas-de-escribir.md).
- ⚠️ Más piezas que operar: 6 JVM, Kafka y MariaDB. Se asume conscientemente porque es el objetivo del
  proyecto. Para un sistema así en producción real, el monolito modular sería la opción razonable.
- 🔁 Si la operación pesa más que el aprendizaje, `importacion` se fusionaría con `movimientos` (ver
  [0003](0003-importacion-como-capa-anticorrupcion.md)) y `consultas` podría volver a ser un módulo.
