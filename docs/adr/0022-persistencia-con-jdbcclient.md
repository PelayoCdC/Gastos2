# ADR 0022: Persistencia con `JdbcClient` y SQL explícito

- **Estado:** aceptada (sustituye a [0007](0007-spring-data-jdbc-y-modelo-de-persistencia-separado.md))
- **Fecha:** 2026-10-21
- **Fase:** F0 (resultado del spike)

## Contexto

El [ADR 0007](0007-spring-data-jdbc-y-modelo-de-persistencia-separado.md) proponía Spring Data JDBC
para el lado de escritura, con registros de persistencia separados del dominio. El spike de F0
(`compartido/pruebas/.../SpikeUuidMariaDbIT`) confirmó dos cosas:

- ✅ El tipo `UUID` nativo de MariaDB funciona sin conversores con el driver 3.5 y Spring JDBC, tanto al
  guardar como al consultar con un parámetro `UUID`.
- ⚠️ `@Version` de Spring Data **incrementa la versión en 1 en cada `save`**. Nuestros agregados
  incrementan una versión **por cada evento** (`RaizAgregado.siguienteVersion()`), así que una operación
  que produce dos eventos avanza dos versiones. Para usar `@Version` habría que:
  - renunciar a que cada evento lleve su propia versión, que es lo que permite a los consumidores
    descartar eventos antiguos con una comparación estricta;
  - o mantener dos contadores distintos.

## Opciones consideradas

1. **Spring Data JDBC con `@Version`**, y que el evento lleve la versión del guardado. Varios eventos de
   la misma operación compartirían versión, así que los consumidores tendrían que usar `<=` y sería
   frágil.
2. **Spring Data JDBC sin `@Version`**, haciendo el bloqueo optimista a mano. Se mezclan dos modelos
   (repositorio genérico y SQL a mano) para lo mismo.
3. **`JdbcClient` con SQL explícito** en cada adaptador de persistencia.

## Decisión

Opción 3.
- Cada adaptador implementa el puerto de repositorio del dominio con `JdbcClient`.
  - **Inserción**, si `agregado.esNuevo()`: `INSERT … version = :version`.
  - **Actualización:** `UPDATE … SET …, version = :version WHERE id = :id AND version = :versionCargada`.
    Si no afecta a ninguna fila, lanza `Conflicto.modificacionConcurrente` (409).
  - **Lectura:** un `RowMapper` reconstituye el agregado con una factoría del dominio (`reconstituir(...)`).
- El "modelo de persistencia separado" es el esquema SQL más el mapeo del adaptador. No hacen falta
  clases `@Table` intermedias.
- Spring Data JDBC no se usa en los servicios. El spike lo sigue usando como prueba del driver.

## Consecuencias

- ✅ La versión de cada evento coincide exactamente con la que se persiste, y el SQL de cada agregado es
  visible y revisable.
- ✅ No hay reflexión ni magia de mapeo, y las operaciones masivas usan la misma herramienta.
- ⚠️ Hay que escribir más SQL: cada adaptador tiene su IT de ida y vuelta contra MariaDB.
