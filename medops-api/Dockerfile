# Multi-stage build: compile with Maven, run on JRE 21.
FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# gosu lets the entrypoint fix volume ownership as root and then drop privileges.
RUN useradd --system --uid 10001 --no-create-home appuser \
    && apt-get update \
    && apt-get install -y --no-install-recommends gosu \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data/clinical-files \
    && chown appuser:appuser /data/clinical-files
COPY --from=build /app/target/medops-api-*.jar /app/app.jar
RUN chown appuser:appuser /app/app.jar

# Starts as root so the entrypoint can chown the clinical-files volume (Docker
# initialises named volumes as root), then execs the JVM as appuser via gosu.
USER root
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["/bin/sh","-c","chown -R appuser:appuser /data/clinical-files && exec gosu appuser java $JAVA_OPTS -jar /app/app.jar"]
