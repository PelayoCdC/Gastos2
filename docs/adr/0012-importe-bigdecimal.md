# ADR 0012: Dinero como `BigDecimal` con escala 2

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (implementado en F0, `kernel/Importe`)

## Contexto

El back antiguo guardaba importes y saldos como `float` de 32 bits, tanto en Java como en MariaDB. Eso
tenía varios efectos:
- las sumas acumulaban errores;
- por encima de unos 131.000 € un `float` no puede representar todos los céntimos (por encima de
  262.144 € los saltos son de 3 céntimos);
- la clave de duplicados dependía de cómo Java imprimía el `float` (`"1850.0"`, `"1.0E7"`).

## Decisión

- **`Importe`** es un value object del kernel: un `BigDecimal` normalizado siempre a **escala 2**
  (redondeo HALF_UP) y acotado a la precisión de `DECIMAL(15,2)`. Es inmutable, con aritmética propia y
  un texto canónico (`toPlainString`).
- **Base de datos:** `DECIMAL(15,2)`.
- **JSON:** número con 2 decimales.
- **El saldo de una transacción es opcional** (nulo si el extracto no trae saldo o si se crea a mano).
  Antes valía 0, lo que falseaba el saldo de la cuenta.
- **Lectura de importes de un extracto** (`importacion`): celdas numéricas con `BigDecimal.valueOf(double)`
  y texto con las reglas de siempre (el último separador `,` o `.` es el decimal).

## Consecuencias

- ✅ Las sumas de informes cuadran al céntimo y la clave de duplicados es estable.
- ✅ Dos importes iguales son `equals` aunque se escriban distinto (`1.5` y `1.50`).
- ⚠️ Los datos migrados del back antiguo arrastran la pérdida de precisión que ya tuvieron al guardarse
  como `float`. Se redondean a 2 decimales al restaurar y se documenta como limitación conocida.
