# 0001. Runtime and framework

Status: accepted (2026-11-08)

## Context
Testcontainers starts the image for each test class, so startup time and image size matter. Java is the
maintainer's main stack and Prowide ISO 20022 is a Java library.

## Decision
Java 21 with Javalin (small, no annotation scanning or reflection-heavy container). No Spring Boot. No native image in
v0.1; revisit once the codec is in place and startup is measured.

## Consequences
- Fast start and a small image, fewer runtime dependencies.
- We write more wiring by hand than Spring would give us.
- XML/JAXB reflection may complicate a later GraalVM build. Measure first.
