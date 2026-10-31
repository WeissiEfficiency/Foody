# syntax=docker/dockerfile:1
# Build-Kontext ist das Repo-Root: docker build -f server/Dockerfile -t foody-server:local .

# Stufe 1: Server bauen (FOODY_SERVER_ONLY lässt die Android-Module weg, kein Android-SDK nötig)
FROM eclipse-temurin:21-jdk@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a AS build
ENV FOODY_SERVER_ONLY=1
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY shared/ shared/
COPY server/ server/
# Gradle-Cache zwischen Builds behalten (BuildKit): Abhängigkeiten nicht bei jedem Rebuild neu laden (E3)
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :server:installDist

# Stufe 2: schlankes Laufzeit-Image ohne Root-Rechte
FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
RUN groupadd --gid 10001 foody \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin foody \
    && mkdir -p /data/photos \
    && chown -R 10001:10001 /data
COPY --from=build /src/server/start/build/install/foody-server /opt/foody
RUN printf '#!/bin/sh\nexec /opt/foody/bin/foody-server admin "$@"\n' > /usr/local/bin/foody-admin \
    && chmod 755 /usr/local/bin/foody-admin
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
USER 10001
EXPOSE 8080
# curl bringt das Basis-Image mit: keine zweite JVM je Prüfung (D5)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s CMD curl -fsS "http://127.0.0.1:${FOODY_PORT:-8080}/health" > /dev/null || exit 1
ENTRYPOINT ["/opt/foody/bin/foody-server"]
