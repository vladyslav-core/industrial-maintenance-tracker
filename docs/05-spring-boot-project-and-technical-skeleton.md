# 05 — Spring Boot project and technical skeleton

## 1. Stage status

The technical skeleton of Industrial Maintenance Tracker has been created and verified.

At this stage, the application can:

- start on Java 21 and Spring Boot 4.1.0;
- start an embedded Tomcat server on port `8080`;
- connect to PostgreSQL running in Docker;
- execute Flyway migrations;
- validate the database schema through Hibernate;
- handle an HTTP request in a Spring MVC controller;
- render a Thymeleaf template;
- load the Spring application context in an automated test.

Domain entities and MVP business functions have not yet been implemented.

## 2. Implemented technology stack

- Java 21
- Spring Boot 4.1.0
- Maven Wrapper
- Spring Web MVC
- Thymeleaf
- Spring Data JPA
- Hibernate
- Bean Validation
- PostgreSQL 18
- Flyway
- Docker Compose
- JUnit through Spring Boot Test

Spring Security will be configured later, after the `User` entity and roles are implemented.

## 3. Current project structure

```text
industrial-maintenance-tracker/
├── docs/
├── src/
│   ├── main/
│   │   ├── java/com/vladyslav/industrialmaintenancetracker/
│   │   │   ├── common/controller/HomeController.java
│   │   │   └── IndustrialMaintenanceTrackerApplication.java
│   │   └── resources/
│   │       ├── db/migration/V1__initial_baseline.sql
│   │       ├── templates/home.html
│   │       └── application.properties
│   └── test/
│       └── java/com/vladyslav/industrialmaintenancetracker/
│           └── IndustrialMaintenanceTrackerApplicationTests.java
├── .env.example
├── compose.yaml
├── mvnw
├── mvnw.cmd
└── pom.xml
```

The project continues to use a feature-oriented package structure. Packages for `user`, `equipment`, `repairrequest`, and `maintenance` will be added when their functionality is implemented.

## 4. Application configuration

The application uses environment variables for database connection settings:

```properties
spring.application.name=industrial-maintenance-tracker

spring.datasource.url=jdbc:postgresql://localhost:${DB_PORT}/${DB_NAME}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

spring.flyway.enabled=true
```

Key decisions:

- database credentials are not stored directly in `application.properties`;
- Hibernate does not create or modify tables automatically;
- Flyway is the only mechanism responsible for database schema changes;
- Hibernate only validates that the Java model matches the existing schema;
- Open Session in View is disabled so database work does not occur during Thymeleaf rendering.

## 5. Environment variables

The repository contains `.env.example` with local development settings:

```dotenv
DB_NAME=industrial_maintenance
DB_USERNAME=maintenance_user
DB_PASSWORD=maintenance_password
DB_PORT=5432
```

The real local `.env` file is not committed to Git. The values above are development defaults and must not be reused as production credentials.

When the application is started from IntelliJ IDEA, these variables are passed through the application Run Configuration.

## 6. PostgreSQL and Docker Compose

PostgreSQL runs in a Docker container created through `compose.yaml`.

The configuration provides:

- the `postgres:18` image;
- a database and user configured through environment variables;
- local access through port `5432` by default;
- a persistent Docker volume for database data.

Start the database:

```powershell
docker compose up -d
```

Check container status:

```powershell
docker compose ps
```

Stop the container without deleting its data:

```powershell
docker compose stop
```

## 7. Flyway baseline

The first migration has been created:

```text
src/main/resources/db/migration/V1__initial_baseline.sql
```

Current contents:

```sql
-- Initial database baseline.
-- Domain tables will be added in later migrations.
```

The migration intentionally does not create domain tables. It verifies that Flyway is connected and establishes version `V1` as the initial database baseline.

On the first application start, Flyway:

1. connected to PostgreSQL;
2. created the `flyway_schema_history` table;
3. applied migration `V1__initial_baseline.sql`;
4. moved the schema to version `1`.

Future schema changes will be added as new migrations. An already applied migration must not be edited.

## 8. Minimal MVC and Thymeleaf flow

The first controller handles the root path:

```java
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
```

The request follows this path:

```text
Browser
  -> GET /
  -> HomeController.home()
  -> return "home"
  -> Thymeleaf resolves templates/home.html
  -> generated HTML is returned to the browser
```

The current `home.html` is deliberately minimal. Its purpose is to verify the complete web request and template-rendering path, not to represent the final interface.

## 9. Running the application locally

1. Ensure Docker Desktop is running.
2. Create a local `.env` from `.env.example` if it does not exist.
3. Start PostgreSQL:

   ```powershell
   docker compose up -d
   ```

4. Pass the variables from `.env` through the IntelliJ IDEA Run Configuration.
5. Run `IndustrialMaintenanceTrackerApplication`.
6. Wait for:

   ```text
   Started IndustrialMaintenanceTrackerApplication
   ```

7. Open:

   ```text
   http://localhost:8080/
   ```

Expected page content:

```text
Industrial Maintenance Tracker
Application is running successfully.
```

## 10. Test verification

The generated `contextLoads` test has been executed successfully:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The test currently loads the complete Spring context and connects to the local PostgreSQL container. Therefore, the database must be running and the four database environment variables must be available to the Maven process.

Example for the current PowerShell session:

```powershell
$env:DB_PORT="5432"
$env:DB_NAME="industrial_maintenance"
$env:DB_USERNAME="maintenance_user"
$env:DB_PASSWORD="maintenance_password"
./mvnw.cmd test
```

These PowerShell variables exist only in the current terminal session.

This is an acceptable temporary verification for the technical skeleton. Proper database integration tests will later use Testcontainers so that tests do not depend on a manually prepared development database. H2 will not be introduced because it would not faithfully reproduce PostgreSQL behavior.

## 11. Verified application path

The following complete path has been verified:

```text
Browser
  -> Spring MVC controller
  -> Thymeleaf template

Spring Boot
  -> HikariCP
  -> PostgreSQL
  -> Flyway
  -> Hibernate schema validation
```

Verified results:

- the application starts without errors;
- PostgreSQL accepts the connection;
- Flyway applies and records migration `V1`;
- Hibernate initializes successfully;
- `GET /` returns the Thymeleaf page;
- the Spring context test passes;
- the implemented changes have been committed and pushed to `main`;
- the Git working tree is clean.

Relevant commits:

- `2d0e48b` — `chore: configure PostgreSQL and Flyway`
- `eff1f71` — `feat: add home controller and Thymeleaf page`

## 12. Deliberately not implemented yet

The following items are outside the technical-skeleton stage:

- domain entities `User`, `Equipment`, `RepairRequest`, and `MaintenanceRecord`;
- domain enums and business rules;
- domain database tables;
- repositories and services;
- DTOs and form validation scenarios;
- Spring Security configuration;
- login and logout;
- role-based authorization;
- final page layout and Bootstrap styling;
- REST API, Swagger/OpenAPI, and CI pipeline;
- complete unit, MVC, and integration test suites.

These parts will be added incrementally instead of being generated all at once.

## 13. Stage completion criteria

Stage 05 is complete because:

- the Spring Boot project builds;
- the application starts on Java 21;
- PostgreSQL runs through Docker Compose;
- environment-based database configuration works;
- Flyway controls database schema versioning;
- Hibernate validation succeeds;
- Spring MVC and Thymeleaf work end to end;
- the application context test passes;
- all implemented changes are stored in GitHub.

## 14. Next stage

The next stage is implementation of users, roles, and authentication.

Recommended order:

1. define the `Role` enum;
2. implement the `User` entity;
3. create the Flyway migration for the users table;
4. add `UserRepository`;
5. add the user service layer and validation rules;
6. add tests for the user module;
7. configure Spring Security;
8. implement login, logout, and role-based access.

Equipment and repair-request modules will be implemented only after the user and authorization foundation is stable.
