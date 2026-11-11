# Twinrail emulator image. Build from the repository root:  docker build -t twinrail .
#
# Stage 1 builds the distribution and creates an AppCDS class archive (ADR 0001) by running the server once
# in warm-up mode. Stage 2 is a plain JRE with the distribution and the archive.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY core core
COPY rule-packs rule-packs
COPY server server
COPY ui ui
COPY testcontainers testcontainers
RUN ./gradlew :server:installDist --no-daemon -x test
# The archive is valid only for the exact class path it was made with, so the run stage uses the same paths.
RUN mkdir -p /opt && cp -r server/build/install/twinrail /opt/twinrail \
 && java -XX:ArchiveClassesAtExit=/opt/twinrail/twinrail.jsa -cp "/opt/twinrail/lib/*" \
      io.github.vthattey.twinrail.server.Main --warmup

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 twinrail
COPY --from=build /opt/twinrail /opt/twinrail
USER twinrail
EXPOSE 8080
ENV TWINRAIL_PORT=8080
# Health: GET /_twinrail/health. Settings: TWINRAIL_SEED, TWINRAIL_START, TWINRAIL_TIMEOUT, TWINRAIL_RULE_PACK.
ENTRYPOINT ["java", "-XX:SharedArchiveFile=/opt/twinrail/twinrail.jsa", "-Xshare:auto", "-XX:TieredStopAtLevel=1", \
            "-cp", "/opt/twinrail/lib/*", "io.github.vthattey.twinrail.server.Main"]
