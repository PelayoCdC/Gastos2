# 4. Escenarios (vista de ejecución)

Diagramas de secuencia de los flujos importantes. Cada escenario se añade (y se verifica contra el
código) en la fase en que se implementa.

| Escenario | Fase | Estado |
|---|---|---|
| Registro de usuario y siembra del catálogo | F1 / F5 | pendiente |
| Inicio de sesión y llamada autenticada a través del gateway | F2 | pendiente |
| Importación de un extracto (importacion → movimientos → clasificación automática → consultas) | F7 | pendiente |
| Crear y clasificar una transacción a mano (dos servicios, *read-through*) | F6 | pendiente |
| Leer lo que acabas de escribir (`Gastos-Secuencia` / `Gastos-Esperar`) | F8 | pendiente |
| Borrar una categoría usada por etiquetas y transacciones | F5 | pendiente |
| Reconstruir consultas reproduciendo Kafka | F9 | pendiente |
| Restaurar un respaldo v1 (migración desde el back antiguo) | F10 | pendiente |
