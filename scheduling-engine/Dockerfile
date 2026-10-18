# syntax=docker/dockerfile:1
# Deployable scheduling-engine (ADR-006: entrypoint = Spring Boot app + Dockerfile).
# Construir desde la RAÍZ del repo: docker build -f scheduling-engine/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -B -pl scheduling-engine -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/scheduling-engine/target/scheduling-engine-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
