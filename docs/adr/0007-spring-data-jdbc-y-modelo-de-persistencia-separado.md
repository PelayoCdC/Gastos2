# ADR 0007: Spring Data JDBC con un modelo de persistencia separado del de dominio

- **Estado:** sustituida por [0022](0022-persistencia-con-jdbcclient.md) tras el spike de F0
- **Fecha:** 2026-10-21
- **Fase:** diseño

## Contexto

Los agregados de dominio son Java puro ([0006](0006-unidad-de-trabajo-en-vez-de-transactional.md)), así
que no pueden llevar anotaciones de persistencia. Además, JPA aporta cosas que en DDD estorban más que
ayudan:
- carga perezosa;
- *dirty checking*;
- relaciones navegables entre agregados (justo el acoplamiento que se quiere evitar).

## Opciones consideradas

1. **JPA/Hibernate** con entidades separadas del dominio.
2. **Spring Data JDBC** con registros de persistencia (`record` con `@Table`) en infraestructura y
   mapeadores. Este enfoque está pensado para agregados: guarda y carga el agregado entero, sin magia.
3. **jOOQ o SQL a mano** para todo.

## Decisión

- **Lado de escritura:** Spring Data JDBC con un modelo de persistencia propio por servicio
  (`infraestructura/persistencia/filas`). Los adaptadores implementan los puertos de repositorio del
  dominio y mapean entre filas y agregados.
- **Operaciones masivas** (registrar miles de líneas de un extracto, reetiquetar, reasignar un
  documento): `JdbcClient` con SQL por conjuntos o por lotes.
- **Lado de lectura** (`consultas`): `JdbcClient` con SQL explícito.
- **Ids:** se asignan en la aplicación (UUIDv7). La columna `version` del registro es nula cuando la
  fila es nueva, para que Spring Data haga INSERT en vez de UPDATE.
- **Bloqueo optimista:** el adaptador lo hace con la `versionCargada` del agregado. Si el `UPDATE` no
  afecta a ninguna fila, lanza `Conflicto.modificacionConcurrente` (409).
- **Tipos en MariaDB:**
  - Los UUID se guardan con el tipo nativo `UUID` (≥ 10.7).
  - El dinero se guarda en `DECIMAL(15,2)`.

## Consecuencias

- ✅ El dominio no sabe nada de la base de datos; el esquema puede cambiar sin tocarlo.
- ✅ No hay sorpresas de carga perezosa ni de flush implícito.
- ⚠️ Hay mapeadores a mano por agregado, con su test de ida y vuelta en los IT de cada adaptador.
- 🔍 **Spike de F0:** verificar que el driver de MariaDB y Spring Data JDBC manejan el tipo `UUID`
  nativo y el `@Version`. El resultado queda en la [bitácora](../proceso/bitacora.md).
