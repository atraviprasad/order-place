# Order Place

B2B furniture ordering platform (monorepo). Java 25, Spring Boot 4.0.6, Maven.

## Layout
- `libs/common-events`, `libs/common-web`, `libs/common-security`: small shared libraries
- `services/*`: api-gateway (8080), user (8081), catalog (8082), dealer (8083), order (8084), payment (8085), notification (8086)
- `infra/`: Postgres init SQL and Keycloak realm export (copy from the orderplace-infra package)
- `docker-compose.yml` (infrastructure, from orderplace-infra) and `docker-compose.apps.yml` (application services)
- `docs/`, `frontend/`: add requirements and the React app

## Build
```
mvn -q -DskipTests package
```
Run one service from the IDE: start `*Application` with the default profile (uses localhost ports from the infrastructure compose).

## Before first build
1. Confirm the Spring Boot 4.0.x patch and Spring Cloud 2025.1.x versions on start.spring.io and update the parent POM.
2. Confirm Boot 4 starter names and third-party versions (springdoc, resilience4j, razorpay-java, minio), which were written without compiling.
3. Copy `.env.example` to `.env`.

## Conventions
Records for DTOs and events; Lombok for JPA entities and Mongo documents (add the Lombok dependency when you write the first entity); avoid `@Data` on entities.
Flyway owns the schema; Hibernate runs with `ddl-auto: validate`.
