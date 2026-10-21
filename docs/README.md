# Documentación de Gastos2

Este directorio explica qué es el sistema, cómo está construido y **por qué** está construido así.
Se actualiza en cada fase: un cambio no está terminado hasta que su documentación también lo está.

## Por dónde empezar

| Si quieres… | Lee |
|---|---|
| Entender qué se busca y con qué límites | [arquitectura/01-objetivos-y-restricciones.md](arquitectura/01-objetivos-y-restricciones.md) |
| Ver el sistema de un vistazo (piezas y relaciones) | [arquitectura/02-contexto-c4.md](arquitectura/02-contexto-c4.md) |
| Aprender el vocabulario del negocio | [dominio/lenguaje-ubicuo.md](dominio/lenguaje-ubicuo.md) |
| Entender cómo se reparte el dominio entre servicios | [dominio/mapa-de-contextos.md](dominio/mapa-de-contextos.md) |
| Saber por qué se hizo algo de cierta manera | [adr/](adr/) (decisiones numeradas) |
| Saber qué cambia respecto al back antiguo | [comparativa-con-gastos-back.md](comparativa-con-gastos-back.md) |
| Ponerte a programar | [proceso/guia-de-desarrollo.md](proceso/guia-de-desarrollo.md) |
| Ver cómo se ha ido construyendo, con los problemas y las desviaciones | [proceso/bitacora.md](proceso/bitacora.md) |

## Mapa completo

### Arquitectura (arc42 resumido + C4)
1. [Objetivos y restricciones](arquitectura/01-objetivos-y-restricciones.md)
2. [Contexto y contenedores (C4 niveles 1 y 2)](arquitectura/02-contexto-c4.md)
3. [Componentes de cada servicio (C4 nivel 3)](arquitectura/03-componentes-c4.md)
4. [Escenarios: diagramas de secuencia](arquitectura/04-escenarios.md)
5. [Conceptos transversales](arquitectura/05-conceptos-transversales.md): outbox/inbox, eventos, seguridad, errores, transacciones
6. [Despliegue](arquitectura/06-despliegue.md)
7. [Riesgos y deuda técnica](arquitectura/07-riesgos-y-deuda.md)

### Dominio (DDD)
- [Lenguaje ubicuo](dominio/lenguaje-ubicuo.md)
- [Mapa de contextos](dominio/mapa-de-contextos.md)
- Contextos: [identidad](dominio/identidad.md) · [movimientos](dominio/movimientos.md) ·
  [clasificación](dominio/clasificacion.md) · [importación](dominio/importacion.md) · [consultas](dominio/consultas.md)

### Decisiones
- [Registro de decisiones de arquitectura (ADR)](adr/README.md)

### Integración
- [Catálogo de eventos](eventos/catalogo.md)
- [Guía de la API](api/guia.md)

### Proceso y operación
- [Bitácora](proceso/bitacora.md)
- [Guía de desarrollo](proceso/guia-de-desarrollo.md)
- [Operación](proceso/operacion.md)

Los documentos que corresponden a fases posteriores existen desde el principio con su índice y se
completan cuando llega la fase. Así el mapa no cambia de forma.
