# Lenguaje ubicuo

Vocabulario del negocio que se usa igual en conversaciones, documentación y código: los nombres de
las clases, los campos y los endpoints salen de aquí. Si un término cambia de significado, se cambia
primero aquí. Entre corchetes, el contexto donde vive el término; un mismo término puede tener un
matiz distinto en cada contexto.

| Término | Definición |
|---|---|
| **Usuario** [identidad] | Persona registrada. Es dueña de todos sus datos; nadie más puede verlos (un recurso ajeno se responde como inexistente). |
| **Cuenta** [movimientos] | Cuenta bancaria del usuario (nombre, banco, descripción). Su nombre es único por usuario. |
| **Saldo de la cuenta** [movimientos] | Saldo que indica el banco en el último movimiento con saldo (el de fecha contable más reciente y, a igualdad, el de mayor orden). No se calcula sumando importes. |
| **Extracto** | Fichero que descarga el usuario de su banco (Excel o CSV) con una fila por movimiento. |
| **Documento** [movimientos] | Registro de una importación de extracto: nombre (editable), nombre del fichero original, fecha, cuenta. Borrarlo borra sus transacciones; reasignarlo a otra cuenta mueve sus transacciones. |
| **Transacción** [movimientos] | Un movimiento bancario: fecha contable, fecha valor, concepto, importe, saldo, nota. Puede venir de un documento o crearse a mano. |
| **Importe** | Cantidad con signo: negativo es un **gasto** (cargo) y positivo un **ingreso** (abono). Siempre con dos decimales exactos. |
| **Concepto** | Texto descriptivo que pone el banco ("COMPRA LUPA SANTANDER", "BIZUM: Cena"). |
| **Clave de concepto** | El concepto sin tildes, en minúsculas, sin espacios repetidos y **sin las fechas que el banco incrusta** ("GOGALIA 04 02 2024" → "gogalia"). Dos transacciones con la misma clave son **similares**. |
| **Orden** | Número de fila del movimiento en su extracto (1 = primera fila de la hoja). Desempata movimientos del mismo día. |
| **Duplicado** | Fila de un extracto que ya estaba registrada (misma fecha, importe y concepto en la misma cuenta o sin cuenta). Los extractos se solapan, así que reimportar no debe duplicar movimientos. |
| **Formato de banco** [importacion] | Cómo leer los extractos de un banco: fila en la que empiezan los datos, columna de cada campo y formato de fecha. Antes se llamaba "configuración de importación". |
| **Previsualización** [importacion] | Primeras filas del fichero como texto, para que el usuario elija las columnas al crear un formato. |
| **Categoría** [clasificacion] | Clasificación principal de una transacción (Alimentación, Salud…). Cada transacción tiene **como mucho una**. Tiene color. |
| **Etiqueta** [clasificacion] | Marca adicional (Supermercado, Farmacia, Bizum…). Una transacción puede tener **varias**. Puede llevar una categoría asociada (ver *cascada*). |
| **Catálogo** [clasificacion] | Conjunto de categorías y etiquetas de un usuario. Al registrarse recibe un catálogo inicial que después es suyo. |
| **Ocurrencia** [clasificacion] | Texto que, si aparece en el concepto (sin distinguir mayúsculas ni tildes), aplica una categoría o etiqueta automáticamente. Sus espacios cuentan: `"bar "` no casa con "barcelona". |
| **Prioridad** [clasificacion] | Orden explícito de categorías y etiquetas (menor = antes). Decide qué categoría gana cuando varias coinciden. |
| **Cascada** [clasificacion] | Si una transacción sin categoría recibe una etiqueta con categoría asociada, hereda esa categoría (la de la etiqueta de mayor prioridad). |
| **Clasificación automática** [clasificacion] | Aplicar las ocurrencias: primero etiquetas (acumulan), después la cascada y después categorías (solo si no tiene, gana la primera por prioridad y nunca pisa una existente). |
| **Origen de una asignación** [clasificacion] | Por qué una transacción tiene una categoría o etiqueta: AUTOMATICA, CASCADA, MANUAL, PROPAGACION, MASIVA o MIGRACION. Decide qué se conserva al *reetiquetar*. |
| **Reetiquetar** [clasificacion] | Reiniciar las asignaciones automáticas (AUTOMATICA y CASCADA) y volver a aplicar las reglas. Las manuales se respetan salvo que se fuerce. |
| **Propagar** [clasificacion] | Copiar la categoría y las etiquetas de una transacción a sus similares. |
| **Aplicación masiva** [clasificacion] | Asignar una categoría o etiquetas a una selección de transacciones, añadiendo o sustituyendo etiquetas. |
| **Gasto recurrente** [consultas] | Concepto que se repite con una cadencia estable (semanal, quincenal, mensual, trimestral o anual) y un importe estable; se anualiza su coste. |
| **Bizum** [consultas] | Transferencia entre particulares; se detecta por el concepto o por la etiqueta "Bizum" y se agrupa por contacto. |
| **Respaldo v1** | JSON con todos los datos de un usuario conservando sus ids. Sirve como copia de seguridad, para migrar desde el back antiguo y para restaurar en la app móvil. |
