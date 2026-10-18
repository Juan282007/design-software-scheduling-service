# syntax=docker/dockerfile:1
# Deployable schedules-api (ADR-006: entrypoint = Spring Boot app + Dockerfile).
# Construir desde la RAÍZ del repo: docker build -f schedules-api/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -B -pl schedules-api -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/schedules-api/target/schedules-api-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
