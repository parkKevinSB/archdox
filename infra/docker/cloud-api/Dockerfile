# syntax=docker/dockerfile:1

FROM gradle:8.14-jdk21 AS builder

WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN chmod +x gradlew && ./gradlew :cloud-api:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=Asia/Seoul

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        tzdata \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --shell /usr/sbin/nologin archdox \
    && mkdir -p /var/lib/archdox /tmp/archdox \
    && chown -R archdox:archdox /var/lib/archdox /tmp/archdox /home/archdox

WORKDIR /app
COPY --from=builder /workspace/cloud-api/build/libs/cloud-api-*.jar /app/cloud-api.jar

ENV SPRING_PROFILES_ACTIVE=local \
    HOME=/home/archdox \
    TMPDIR=/tmp/archdox

USER archdox
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/cloud-api.jar"]
