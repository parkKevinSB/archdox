# syntax=docker/dockerfile:1

FROM gradle:8.14-jdk21 AS builder

WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN chmod +x gradlew && ./gradlew :archdox-agent:bootJar --no-daemon

FROM gradle:8.14-jdk21 AS pdf-smoke-test

WORKDIR /workspace
USER root
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        fontconfig \
        fonts-noto-cjk \
        fonts-noto-cjk-extra \
        libreoffice-java-common \
        libreoffice-writer \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -f \
    && chown -R gradle:gradle /workspace
USER gradle
COPY --chown=gradle:gradle . .
ENV DOCUMENT_EXPORT_LIBREOFFICE_PATH=soffice \
    SAL_USE_VCLPLUGIN=svp
RUN chmod +x gradlew \
    && ./gradlew :archdox-agent:test \
        --tests "com.archdox.agent.cloud.DocumentRenderCommandExecutorPdfSmokeTest" \
        -Darchdox.agent.pdf-smoke.enabled=true \
        --no-daemon

FROM eclipse-temurin:21-jre-jammy

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=Asia/Seoul

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        fontconfig \
        fonts-noto-cjk \
        fonts-noto-cjk-extra \
        libreoffice-java-common \
        libreoffice-writer \
        tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -f

RUN useradd --create-home --shell /usr/sbin/nologin archdox \
    && mkdir -p /var/lib/archdox-agent /var/log/archdox /tmp/archdox \
    && chown -R archdox:archdox /var/lib/archdox-agent /var/log/archdox /tmp/archdox /home/archdox

WORKDIR /app
COPY --from=builder /workspace/archdox-agent/build/libs/archdox-agent-*.jar /app/archdox-agent.jar

ENV SPRING_PROFILES_ACTIVE=local \
    HOME=/home/archdox \
    TMPDIR=/tmp/archdox \
    LOG_PATH=/var/log/archdox \
    SAL_USE_VCLPLUGIN=svp \
    AGENT_LOCAL_STORAGE_ROOT=/var/lib/archdox-agent \
    DOCUMENT_EXPORT_LIBREOFFICE_ENABLED=true \
    DOCUMENT_EXPORT_LIBREOFFICE_PATH=soffice \
    DOCUMENT_EXPORT_LIBREOFFICE_TIMEOUT_MS=60000

USER archdox
EXPOSE 18080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:18080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/archdox-agent.jar"]
