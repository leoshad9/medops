# Multi-stage build: compile with Maven, run on JRE 21.
FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home appuser
COPY --from=build /app/target/medops-api-*.jar /app/app.jar
RUN chown appuser:appuser /app/app.jar

USER appuser
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
