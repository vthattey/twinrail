# 0009. v0.1 RTR scope follows the published message set

Status: accepted (2026-11-08)

## Context
The brief's MVP lists `pacs.004` returns and `camt.056` recalls answered by `camt.029` for RTR. The RTR message
page and companion document (v1.4) list only `head.001`, `pacs.008`, `pacs.002`, `pacs.028` and `admi.002/004/011`
(see `docs/research.md`, section 2). Payments are described as irrevocable. Launch date is unconfirmed.

## Decision
The RTR pack in v0.1 covers the published set: `pacs.008` in, `pacs.002` accept/reject, settlement commit
advice, `pacs.028` status request, `admi.002` reject, heartbeat, and time-out. Returns and recalls stay in the
NACHA leg (returns and NOCs) and move to the roadmap for RTR until a public spec covers them.

## Consequences
- The demo GIF (time-out) still works.
- The `return` and `recall` scenario actions are scheme-dependent (ADR 0004).
- The brief's MVP item 1 is updated to match.
