# ADR 0021: Código, paquetes de capas y documentación en español

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** F0

## Contexto

- El dominio de Gastos se expresa en español (transacción, ocurrencia, cascada…), y el back antiguo ya
  usaba español en clases y endpoints.
- Sin embargo, sus paquetes de capas estaban en inglés (`domain`, `application`, `infrastructure`).
- El plan inicial de Gastos2 repetía esa mezcla, que obliga a cambiar de idioma a mitad de la ruta de
  cada clase.

## Decisión

- **Todo en español:**
  - las clases y los métodos;
  - los paquetes de capas (`dominio`, `aplicacion`, `infraestructura`);
  - los endpoints, los eventos, los mensajes de error y la documentación.
- **Se mantiene el inglés solo en lo que viene del framework o de estándares:** `Controller`, `Config`,
  `Repository` cuando se extienden interfaces de Spring, y `traceparent`.
- **Los identificadores Java no llevan tildes ni eñes** (`aplicacion`, `tamano`), por compatibilidad con
  herramientas. Los textos y la documentación sí.

## Consecuencias

- ✅ El lenguaje ubicuo llega hasta la estructura del código sin traducciones.
- ✅ Las reglas de ArchUnit usan `..dominio..`, `..aplicacion..` y `..infraestructura..`.
- ⚠️ Algunos nombres quedan algo forzados sin eñe (`tamano`); es una convención aceptada.
