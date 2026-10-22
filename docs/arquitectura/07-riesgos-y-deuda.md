# 7. Riesgos y deuda técnica

## Riesgos vivos

| Riesgo | Impacto | Mitigación | Estado |
|---|---|---|---|
| Novedades de Boot 4 y Spring Cloud 2025.1 (Jackson 3, starters modulares, Testcontainers 2, propiedades del gateway) | Tiempo perdido en incompatibilidades | Versiones fijadas; spikes en F0; el verificador de compatibilidad de Spring Cloud falla pronto | Abierto |
| Consistencia eventual visible en la interfaz | Datos viejos tras guardar | `Gastos-Secuencia` + `Gastos-Esperar` ([ADR 0011](../adr/0011-leer-lo-que-acabas-de-escribir.md)) y proyección por lotes | Mitigado (F8); la clasificación automática tras importar no la cubre la secuencia de la importación |
| Arranque de consultas | Tras recrear su contenedor, el consumidor tardó unos 20 s en recibir sus particiones (reequilibrado del grupo); mientras, las consultas con `Gastos-Esperar` responden con `Gastos-Desfase` | Es lo previsto: el cliente sabe que los datos pueden ir atrasados. Se puede acortar con pertenencia estática al grupo (`group.instance.id`) | Aceptado; se revisa en F11 |
| Importaciones muy grandes (miles de filas) | Medido en F8 con 2000 filas: consultas tarda 2,6 s en proyectarlas (cerca del tope de 3 s de `Gastos-Esperar`) y la clasificación automática ~13 s en verse, porque clasificacion procesa mensaje a mensaje. Con 150 filas: 224 ms y ~1 s | Un extracto mensual tiene decenas o cientos de filas. Si hiciera falta: en consultas, una sentencia por evento (upsert con `IF(version < ...)`) y lotes JDBC; en clasificacion, listener por lotes con el catálogo cargado una vez | Aceptado; se revisa en F11 midiendo |
| Pérdida del volumen de Kafka | No se podría reconstruir `consultas` | Respaldo del volumen; `republicar-estado` (F11) | Abierto |
| Hueco de orden entre commits del outbox | Un evento podría adelantar a otro anterior | Versiones por agregado en los consumidores | Aceptado |
| Memoria (6 JVM + MariaDB + Kafka, más los contenedores de Testcontainers durante `verify`) | Portátil justo de recursos: en F4 una JVM de test murió por falta de memoria nativa | JVM de test acotadas (`argLine` con `-Xmx768m`, C1 y SerialGC); parar los servicios de compose durante `verify`; límites por contenedor; medir en F11 | Mitigado |
| Windows: finales de línea, *named pipe* de Docker, zona horaria | Fallos difíciles de reproducir | `.gitattributes`; Testcontainers 2; `Clock` con zona explícita | Abierto |

## Deuda técnica asumida

| Deuda | Por qué se asume | Cuándo se pagaría |
|---|---|---|
| Sin *refresh token* ni revocación de JWT | Igual que antes; aplicación personal | Si se expone a Internet |
| Sin alta disponibilidad (un nodo de Kafka, una MariaDB) | Uso personal | Nunca, salvo un cambio de uso |
| Importes migrados con la imprecisión del `float` original | No se puede recuperar la información perdida | — |
| La cabecera `Location` de los 201 es relativa al servicio (`/formatos/{id}`), no a la ruta pública (`/api/importacion/formatos/{id}`), porque el gateway no la reescribe | Ningún cliente la usa todavía (el front lee el id del cuerpo) | F11: filtro `RewriteResponseHeader` por ruta en el gateway, o `X-Forwarded-Prefix` + `ForwardedHeaderFilter` |
