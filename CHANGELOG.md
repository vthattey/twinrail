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
- `core`: ACH (NACHA) file codec: table-driven record layouts, parser, deterministic writer, builder that derives
  control records, and a control-total checker with stable problem ids. Guide: `docs/nacha.md`. Research notes on
  the sources read: `docs/research.md` section 7.
- Scenario actions `return` (ACH return codes) and `noc` (notification of change), `nacha.entry` as a scenario
  message type, a return and change code table with a source on every code, and ADR 0011.
- `core`: ACH engine: entries settle against the ledger, a debit the account cannot cover is returned R01, and
  `return` / `noc` scenarios apply on the virtual clock, reporting the return time frame from the code table and
  the type 99 / 98 addenda record. `server`: `POST /ach/files`, `/_twinrail/ach/entries`, `achTransitions` on the
  clock endpoints, OpenAPI and contract tests for them. `Ledger.book` for movements that need no reservation.
- Inspector UI at `/ui`: a live timeline of messages, payment and ACH state changes and clock moves, with the rule
  behind each rejection or return, accounts, webhook deliveries and clock buttons. Server-rendered with vendored
  htmx 2.0.4 (no CDN), escaped by construction, strict Content-Security-Policy. Guide: `docs/ui.md`.
- `testcontainers`: `TwinrailContainer` (Testcontainers 2.0) and a typed `TwinrailClient` for the control API,
  published as `twinrail-testcontainers` (sources and javadoc jars, POM, optional signing). A standalone JUnit 5
  example in `examples/junit5`, and CI steps that run the module's real-image test and the example against the
  built image. Guide: `docs/testcontainers.md`.
- Quickstart and limitations pages, a README that states what works and what does not, and example messages and
  scenario files under `examples/`.
- `scripts/determinism-check.sh` and a CI step: three separate JVM launches must give byte-identical responses.

### Fixed
- Responses are now byte-identical across JVM launches. A rejection's keys, a webhook target's keys and the
  corrected values of a change notification were emitted in an order that changed from one JVM run to the next
  (`Map.of` and `Map.copyOf`); they now keep a fixed order.
