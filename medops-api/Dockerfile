# Multi-stage build: compile with Maven, run on JRE 21.
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

# Download and verify the OpenTelemetry Java agent while this stage still has a
# full JDK. The runtime image is eclipse-temurin:25-jre-jammy, which ships no
# unzip, so `unzip -tq` cannot check the download later. Listing the archive with
# the jar tool is an equivalent integrity check: a 404 HTML body or a truncated
# file fails here instead of becoming a jar that only breaks once -javaagent is
# actually switched on.
ARG OTEL_AGENT_VERSION=2.32.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /tmp/otel-agent.jar
RUN jar tf /tmp/otel-agent.jar > /tmp/otel-manifest.txt \
    && test -s /tmp/otel-manifest.txt \
    && grep -q 'META-INF/MANIFEST.MF' /tmp/otel-manifest.txt

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# gosu lets the entrypoint fix volume ownership as root and then drop privileges.
# curl is here for the container healthcheck only: the jre-jammy base has neither
# curl nor wget, which is why the previous check shelled out to bash /dev/tcp.
RUN useradd --system --uid 10001 --no-create-home appuser \
    && apt-get update \
    && apt-get install -y --no-install-recommends gosu curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data/clinical-files \
    && chown appuser:appuser /data/clinical-files
COPY --from=build /app/target/medops-api-*.jar /app/app.jar
RUN chown appuser:appuser /app/app.jar

# OpenTelemetry Java agent, baked into the image rather than bind-mounted from
# the host. A mount would need a path that exists only on whichever machine set
# it up, which is exactly how "works locally, missing in prod" happens.
#
# Inert by default: -javaagent is only passed when OTEL_ENABLED=true, set solely
# by the observability overlay's otel profile. So this costs ~15 MB of image size
# on every build and nothing at runtime until traces are wanted.
COPY --from=build /tmp/otel-agent.jar /otel/opentelemetry-javaagent.jar
RUN chown appuser:appuser /otel/opentelemetry-javaagent.jar

# Starts as root so the entrypoint can chown the clinical-files volume (Docker
# initialises named volumes as root), then execs the JVM as appuser via gosu.
USER root
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
# OTEL_ENABLED gates the agent. Only the observability overlay sets it, so the
# default path is an uninstrumented JVM and the OTEL_* values below are inert.
# Both halves matter: OTEL_ENABLED controls whether -javaagent reaches the command
# line at all, and OTEL_EXPORTER_OTLP_ENDPOINT is what the agent reads once it is.
ENV OTEL_ENABLED=false
ENV OTEL_SERVICE_NAME=medops-api
# -javaagent must precede -jar or the JVM rejects it, and $JAVA_OPTS expands ahead
# of -jar in this entrypoint. JAVA_TOOL_OPTIONS is deliberately not used: it logs
# a "Picked up ..." line on every start, which would pollute the deploy output that
# the deploy workflow tails on failure.
ENTRYPOINT ["/bin/sh","-c","chown -R appuser:appuser /data/clinical-files && if [ \"$OTEL_ENABLED\" = \"true\" ]; then exec gosu appuser java $JAVA_OPTS -javaagent:/otel/opentelemetry-javaagent.jar -jar /app/app.jar; else exec gosu appuser java $JAVA_OPTS -jar /app/app.jar; fi"]
