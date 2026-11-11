# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Project brief, research notes, architecture decision records and the v0.1 plan under `docs/`.
- Multi-module Gradle build (`core`, `rule-packs`, `ui`, `server`, `testcontainers`) on Java 21.
- Apache-2.0 licence, contributing guide, security policy and code of conduct.
- CI on Linux, Windows and macOS.
- `core`: virtual clock, seeded id generator, ISO 20022 message codec (Prowide open edition) with fixed output
  settings, in-memory recorder and versioned session files, with a determinism test.
- `core`: rule engine and rule-pack loader (YAML, published JSON Schema); rejections name the rule, reason code
  and source specification. Scenario files with first-match-wins matching and reject, delay and drop actions.
  Restricted XPath-like element paths. An illustrative example pack. Guides: `docs/rule-packs.md`,
  `docs/scenarios.md`.
- `core`: payment lifecycle (accepted, settled, rejected, timed out) driven by the virtual clock; rules, then
  scenarios, then a funds reservation. Ledger with balances per test account and `camt.053.001.08` statements and
  `camt.054.001.08` notifications generated from it. Guide: `docs/lifecycle.md`.
- `server`: Javalin server with `POST /messages` and the `/_twinrail/` control API (clock, accounts, statements,
  scenarios, payments, session export, reset). Guide: `docs/control-api.md`.
- `server`: signed webhooks (HMAC-SHA256) for payment state changes, with retries scheduled on the virtual clock
  and local-only targets.
- `server`: OpenAPI 3.1 description of the message endpoint, control API and webhook event, served at
  `/_twinrail/openapi.yaml` and contract-tested against the server. Account balances are now JSON strings.
- Dockerfile (multi-stage, AppCDS class archive, unprivileged user), `scripts/image-check.sh`, a CI job that builds
  the image, smoke-tests it and records size and start-up time, and a workflow that publishes the image on version
  tags. `server`: runnable distribution (`installDist`), eager codec warm-up at start. Guide: `docs/docker.md`.
