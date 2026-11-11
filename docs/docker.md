# Running the image

```sh
docker build -t twinrail .
docker run --rm -p 8080:8080 twinrail
curl http://localhost:8080/_twinrail/health
```

Settings are environment variables: `TWINRAIL_PORT` (8080), `TWINRAIL_SEED` (1), `TWINRAIL_START` (virtual clock
start, `2026-01-01T00:00:00Z`), `TWINRAIL_TIMEOUT` (`PT30S`) and `TWINRAIL_RULE_PACK` (a pack file mounted into
the container; the default is the bundled illustrative example pack, which is not a scheme). The API is described
in [control-api.md](control-api.md).

The container makes no outbound connections except webhooks to local targets, so it also runs without a network.
It runs as an unprivileged user.

## How it is built

A multi-stage build: Gradle builds the distribution, then the server runs once in `--warmup` mode with
`-XX:ArchiveClassesAtExit` to create an AppCDS class archive (ADR 0001), and the final image is a plain Temurin 21
JRE with the distribution and that archive. At start the server also creates the JAXB context eagerly, so the
first request is not the slow one.

## Size and start-up

CI builds the image on every push, starts it, sends a payment, and records the image size and the time from
`docker run` to a healthy answer in the job summary (`scripts/image-check.sh`). Those numbers, not estimates, are
the reference. On the maintainer's Windows laptop, outside Docker, start-up to a healthy answer was about 6.5 s
without the class archive and about 4.4 s with it (both include Jetty, Javalin and the JAXB context); expect a
faster time on Linux CI. Start-up has not yet been tuned.

## Publishing

Pushing a version tag such as `v0.1.0` runs `.github/workflows/release-image.yml`: it builds the image, runs the
same check, and only then pushes `ghcr.io/<owner>/twinrail:<version>` and `:latest`.

> Status: the Dockerfile and workflows are written but had not been run when this page was written (there is no
> Docker on the development machine). The first CI run is their first test.
