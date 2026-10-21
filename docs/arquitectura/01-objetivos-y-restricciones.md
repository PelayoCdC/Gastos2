# 1. Objetivos y restricciones

## Qué es Gastos

Una aplicación personal para controlar gastos:
1. Se importan los extractos bancarios en Excel o CSV.
2. Los movimientos se clasifican con **categorías** (una por movimiento) y **etiquetas** (varias). La
   clasificación se hace automáticamente mediante reglas (ocurrencias de texto) y a mano.
3. Se consultan informes: evolución mensual, saldo por cuenta, gastos por categoría, gastos recurrentes,
   Bizum…

Hay tres clientes:
- `gastos-front`, una SPA en Angular.
- `gastos-movil`, una app Android 100 % local que importa los datos del back mediante un JSON de
  exportación.
- El propio usuario, que es el único que usa el sistema, aunque este es multiusuario.

## Por qué se reescribe

El back actual (`gastos-back`) funciona, pero su arquitectura tiene problemas que dificultan
evolucionarlo:

- **Hexagonal solo de nombre.** Las entidades son JPA anémicas con `@ManyToOne` entre módulos. Hay
  ciclos entre paquetes, la capa de aplicación depende de la de infraestructura y los mappers tienen
  lógica de negocio.
- **Defectos de dominio:**
  - el dinero se guarda en `float`;
  - los nombres pueden repetirse, aunque todo el código asume que no;
  - "la primera regla que coincide" depende del orden en que la base de datos devuelve las filas;
  - borrar una categoría o una cuenta referenciada da un error 500.

  El detalle está en [la comparativa](../comparativa-con-gastos-back.md).

## Objetivos de esta versión

Por orden de prioridad:

1. **Aprender y aplicar bien** DDD (estratégico y táctico), Clean Architecture, arquitectura hexagonal y
   microservicios, con un código que sirva de referencia.
2. **Mantener toda la funcionalidad** del back antiguo y migrar los datos reales sin pérdida
   (ADR [0020](../adr/0020-restauracion-v1-con-uuid-originales.md)).
3. **Corregir los defectos conocidos** (ADR 0012–0017).
4. **Que cada decisión quede documentada con su porqué** (ADR, esta documentación y la bitácora).

## Atributos de calidad buscados

| Atributo | Cómo se concreta |
|---|---|
| Mantenibilidad / evolución | Contextos acotados con modelo propio, regla de dependencia verificada con ArchUnit, eventos versionados |
| Corrección del dominio | Reglas en el dominio con tests unitarios; casos portados de los tests del back antiguo y de la app móvil |
| Consistencia | Atómica dentro de cada servicio; eventual entre servicios, con garantías explícitas (outbox, versiones, idempotencia) |
| Testabilidad | Dominio y aplicación sin frameworks; tests de integración con contenedores reales (Testcontainers) |
| Seguridad | Aislamiento por usuario en cada caso de uso; JWT firmado con clave asimétrica; los endpoints internos no se exponen |
| Recuperabilidad | El lado de lectura se puede reconstruir desde el log de eventos; respaldo y restauración en formato v1 |

## Restricciones

- **Una sola persona** desarrolla y opera el sistema, en un portátil con **Windows** y Docker Desktop.
  Esto limita la complejidad operativa aceptable:
  - nada de Kubernetes, service mesh o registro de servicios;
  - un único `compose.yaml`;
  - un nodo de Kafka y una instancia de MariaDB, con una base de datos por servicio.
- **Stack:** Java 21, Spring Boot 4.1 y Spring Cloud 2025.1, MariaDB 11.8 y Kafka 4.2 (KRaft). Maven con
  wrapper.
- **Recursos:** seis JVM más MariaDB y Kafka deben caber en unos 3 GB de RAM.
- **El front y el móvil no se tocan en este proyecto.** La API se rediseña libremente (el front se
  adaptará después), pero la **exportación v1** debe ser compatible con la restauración de `gastos-movil`.

## Fuera de alcance

- Adaptar `gastos-front` a la nueva API.
- Desplegar en un entorno que no sea local, aunque la configuración no lo impide.
- Alta disponibilidad: un fallo de Kafka o MariaDB detiene el sistema, cosa aceptable para un uso
  personal.
