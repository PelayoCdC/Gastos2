# ADR 0018: Monorepo Maven multi-módulo

- **Estado:** aceptada
- **Fecha:** 2026-10-21
- **Fase:** diseño (implementado en F0)

## Contexto

Seis servicios más módulos compartidos (kernel, contratos de eventos, soporte web y de mensajería,
soporte de pruebas). Una sola persona los desarrolla. Los contratos de eventos los usan productores y
consumidores, y deben evolucionar de forma coordinada.

## Opciones consideradas

1. **Un repositorio por servicio**, como en una organización grande. Habría que publicar y versionar
   las librerías compartidas y mantener varios pipelines, una fricción que aquí no aporta nada.
2. **Monorepo con un proyecto Maven por módulo** y un POM raíz agregador.

## Decisión

Opción 2.
- **Organización del build:**
  - El POM raíz hereda de `spring-boot-starter-parent` (4.1.x), que gestiona las versiones del
    ecosistema.
  - Importa `spring-cloud-dependencies` (2025.1.x).
  - Declara las versiones de terceros no gestionados (POI, ArchUnit, springdoc).
- **Módulos:** `compartido/*` (kernel, contratos, web, mensajeria, pruebas), `servicios/*` y
  `pruebas-e2e`.
- **Independencia de los servicios:** cada servicio sigue siendo un jar y un contenedor independiente.
  Solo comparte código a través de los módulos `compartido`.
- **Herramientas de build:**
  - Maven Wrapper (Maven 3.9.9), copiado del back antiguo.
  - `maven-enforcer` exige Java 21 y Maven 3.9, y prohíbe frameworks en `kernel` y `contratos`.
  - Surefire ejecuta los `*Test` y failsafe los `*IT`.

## Consecuencias

- ✅ Un solo `./mvnw verify` comprueba todo el sistema, incluidos los contratos entre servicios.
- ✅ Los cambios que cruzan servicios (por ejemplo, un evento nuevo) se hacen en un solo commit.
- ⚠️ Es fácil caer en compartir de más. Lo frenan la regla de admisión del kernel
  ([mapa de contextos](../dominio/mapa-de-contextos.md)) y los contratos versionados.
- ⚠️ Los servicios no se versionan por separado. Para un único desarrollador no importa.
