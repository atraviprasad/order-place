# Build from the repository root:  docker build -f services/notification-service/Dockerfile -t orderplace/notification-service .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q -pl services/notification-service -am -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /workspace/services/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
