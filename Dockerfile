# syntax=docker/dockerfile:1
#
# Una imagen por servicio desde un único Dockerfile (docs/arquitectura/06-despliegue.md):
#   docker build --target identidad -t gastos2/identidad .
# compose.yaml elige el target de cada servicio. La etapa "build" compila todo el monorepo una vez
# y la caché de ~/.m2 se conserva entre builds (BuildKit).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY compartido compartido
COPY servicios servicios
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -q package -DskipTests -DskipITs

FROM eclipse-temurin:21-jre AS base
WORKDIR /app
ENV TZ=Europe/Madrid
EXPOSE 8080
# curl viene en la imagen: lo usan los healthcheck de compose contra /actuator/health
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar", "--server.port=8080"]

FROM base AS identidad
COPY --from=build /src/servicios/identidad/target/identidad.jar app.jar

FROM base AS gateway
COPY --from=build /src/servicios/gateway/target/gateway.jar app.jar

FROM base AS movimientos
COPY --from=build /src/servicios/movimientos/target/movimientos.jar app.jar
