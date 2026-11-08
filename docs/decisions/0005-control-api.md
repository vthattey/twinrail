# 0005. Control API and webhooks

Status: accepted (2026-11-08)

## Context
Tests in any language must drive the clock, scenarios and accounts, and read what happened.

## Decision
- REST under `/_twinrail/` (for example `clock`, `scenarios`, `accounts`, `messages`, `reset`), JSON, with an
  OpenAPI spec published in the repo and tested against the server.
- Webhooks are POSTs with a fixed JSON envelope (event id, type, time from the virtual clock, payload) and an HMAC
  signature header.
- Errors name what went wrong and what to do next.

## Consequences
- Non-Java users get a stable contract; the Java client in the Testcontainers module is a thin wrapper.
- The OpenAPI file is a compatibility surface: changes need CHANGELOG entries.
- Webhook delivery is local only (no internet), so targets are reachable from the container.
