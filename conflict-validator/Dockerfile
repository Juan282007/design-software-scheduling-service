# syntax=docker/dockerfile:1
# Deployable conflict-validator (ADR-006: entrypoint = Spring Boot app + Dockerfile).
# Construir desde la RAÍZ del repo: docker build -f conflict-validator/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -B -pl conflict-validator -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/conflict-validator/target/conflict-validator-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
