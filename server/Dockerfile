# Build-Kontext ist das Repo-Root: docker build -f server/Dockerfile -t foody-server:local .

# Stufe 1: Server bauen (FOODY_SERVER_ONLY lässt die Android-Module weg, kein Android-SDK nötig)
FROM eclipse-temurin:21-jdk AS build
ENV FOODY_SERVER_ONLY=1
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY shared/ shared/
COPY server/ server/
RUN ./gradlew --no-daemon :server:installDist

# Stufe 2: schlankes Laufzeit-Image ohne Root-Rechte
FROM eclipse-temurin:21-jre
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
# Jede Prüfung startet eine JVM – daher nur alle 60 s
HEALTHCHECK --interval=60s --timeout=10s --start-period=30s CMD ["/opt/foody/bin/foody-server","healthcheck"]
ENTRYPOINT ["/opt/foody/bin/foody-server"]
