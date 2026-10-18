# design-software-scheduling-service (Java/Spring Boot)

Esqueleto hexagonal Maven multi-módulo (ADR-006): core (dominio + aplicación puros) / adapters / apps.

Módulos: scheduling-core, scheduling-adapters, schedules-api, scheduling-engine, conflict-validator. Build: `./mvnw package` (requiere Maven y Java 17).

## Conexión a la DB

Los datos de conexión no van en el código, solo como variables de entorno en la terminal:

```powershell
$env:SCHEDULING_DB_URL="jdbc:postgresql://localhost:15432/design-software-develop"
$env:SCHEDULING_DB_USERNAME="design_software_user"
$env:SCHEDULING_DB_PASSWORD="changeme" # la de tu .env.develop
```

## Levantar la API

Desde el módulo `schedules-api`:

```powershell
..\mvnw.cmd spring-boot:run
```

## Nota: arreglo para compilar en Windows (contracts/pom.xml)

Se cambió la forma de apuntar al archivo de contrato para que funcione tanto en Windows como en otros sistemas. Antes el proyecto no compilaba en Windows y se quedaba frenado sin dejar arrancar la API. Ahora ya compila normal con `./mvnw install`.
