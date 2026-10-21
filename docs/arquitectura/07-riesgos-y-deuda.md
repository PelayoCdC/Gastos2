# 7. Riesgos y deuda técnica

## Riesgos vivos

| Riesgo | Impacto | Mitigación | Estado |
|---|---|---|---|
| Novedades de Boot 4 y Spring Cloud 2025.1 (Jackson 3, starters modulares, Testcontainers 2, propiedades del gateway) | Tiempo perdido en incompatibilidades | Versiones fijadas; spikes en F0; el verificador de compatibilidad de Spring Cloud falla pronto | Abierto |
| Consistencia eventual visible en la interfaz | Datos viejos tras guardar | `Gastos-Esperar` ([ADR 0011](../adr/0011-leer-lo-que-acabas-de-escribir.md)) | Abierto (F8) |
| Pérdida del volumen de Kafka | No se podría reconstruir `consultas` | Respaldo del volumen; `republicar-estado` (F11) | Abierto |
| Hueco de orden entre commits del outbox | Un evento podría adelantar a otro anterior | Versiones por agregado en los consumidores | Aceptado |
| Memoria (6 JVM + MariaDB + Kafka) | Portátil justo de recursos | Límites por contenedor; medir en F11 | Abierto |
| Windows: finales de línea, *named pipe* de Docker, zona horaria | Fallos difíciles de reproducir | `.gitattributes`; Testcontainers 2; `Clock` con zona explícita | Abierto |

## Deuda técnica asumida

| Deuda | Por qué se asume | Cuándo se pagaría |
|---|---|---|
| Sin *refresh token* ni revocación de JWT | Igual que antes; aplicación personal | Si se expone a Internet |
| Sin alta disponibilidad (un nodo de Kafka, una MariaDB) | Uso personal | Nunca, salvo un cambio de uso |
| Importes migrados con la imprecisión del `float` original | No se puede recuperar la información perdida | — |
