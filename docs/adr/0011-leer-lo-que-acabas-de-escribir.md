# ADR 0011: Leer lo que acabas de escribir con secuencias (`Gastos-Esperar`)

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (se implementa en F8)

## Contexto

Con CQRS, después de editar una transacción en `movimientos` o `clasificacion`, la rejilla de
`consultas` puede no reflejar el cambio todavía. La interfaz mostraría datos viejos justo después de
guardar, que es la peor experiencia posible.

## Opciones consideradas

1. **Ignorarlo** y que el usuario recargue.
2. **Actualización optimista en el front.** Funciona para casos simples, pero no para informes ni para
   efectos en cascada.
3. **Token de consistencia:**
   - La respuesta de cada comando indica hasta qué evento llega el cambio.
   - `consultas` espera, con un tope, a haber procesado ese evento antes de responder.

## Decisión

Opción 3.
- Cada respuesta de comando incluye la cabecera `Gastos-Secuencia: <contexto>=<n>`, donde `n` es el id
  del outbox del último evento escrito.
- El front recuerda el máximo por contexto y lo envía en `Gastos-Esperar: movimientos=18342,clasificacion=977`.
- `consultas` guarda, por usuario y contexto, la última secuencia procesada.
  - Si no ha llegado a la pedida, espera sondeando cada 50 ms, hasta 3 s.
  - Si se agota el tiempo, responde igualmente con `Gastos-Desfase: true`.

## Consecuencias

- ✅ "Guardar y ver el cambio" funciona con un único interceptor en el front, sin lógica por pantalla.
- ✅ Si `consultas` va retrasada, el cliente lo sabe por la cabecera y puede avisar.
- ⚠️ Las peticiones con `Gastos-Esperar` pueden tardar más. El tope de 3 s acota el peor caso.
- ⚠️ La clasificación automática tras importar es un efecto asíncrono de otro servicio: la secuencia de
  la importación no la cubre. La pantalla de importación lo explica ("clasificando…").

## Notas de implementación (F8, 2026-10-22)

- **La cabecera la pone un filtro de `compartido/mensajeria`** (`FiltroSecuenciaPublicada`), no cada
  controlador. `PublicadorEventosOutbox` anota la secuencia de cada evento escrito en el hilo de la
  petición, y el filtro envuelve la respuesta para poner la cabecera en el último momento: al empezar a
  escribir el cuerpo o, si no hay cuerpo (204), al terminar. Las respuestas de error y las lecturas no la
  llevan.
- **Un comando puede devolver secuencias de varios contextos** (`movimientos=12,clasificacion=3`).
  importacion no escribe los eventos de una importación (los escribe movimientos), así que reenvía la
  `Gastos-Secuencia` que le devuelve movimientos.
- **consultas ignora los contextos que no proyecta.** Esperar a `identidad=5` agotaría siempre los 3 s;
  se descarta antes de esperar.
- **Una cabecera mal formada es un 400** (`CABECERA_INVALIDA`) en vez de ignorarse: si se ignorara, el
  front creería estar leyendo datos al día.
- **El tope se cuenta por intentos** (60 de 50 ms) y no por reloj, para poder probarlo sin dormir.
