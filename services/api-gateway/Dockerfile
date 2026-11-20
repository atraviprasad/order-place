# Build from the repository root:  docker build -f services/api-gateway/Dockerfile -t orderplace/api-gateway .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q -pl services/api-gateway -am -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /workspace/services/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
