# 0008. Repo layout and build

Status: accepted (2026-11-08)

## Context
The Testcontainers module must be published separately from the server image.

## Decision
Multi-module Gradle (Kotlin DSL) build: `core` (codec, rule engine, lifecycle, clock, ledger, recorder),
`rule-packs` (data and schemas), `server` (Javalin, transports, control API), `ui`, `testcontainers`. Root
also holds `docs/`, CI, and community files.

## Consequences
- Independent versioning and publishing are possible.
- More build files up front.
- CI matrix covers Linux, Windows and macOS.
