# Build from the repository root:  docker build -f services/catalog-service/Dockerfile -t orderplace/catalog-service .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q -pl services/catalog-service -am -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /workspace/services/catalog-service/target/catalog-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
