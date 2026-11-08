# Twinrail — project brief

> **The offline twin of the payment rails.** One Docker image that plays the payment scheme and the other
> bank, speaking real ISO 20022, for local development and CI. It holds no money and connects to nothing.
> Short pitch: *LocalStack, but for payment rails.*

## Ground rules

- Public, open-source project under `github.com/vthattey/twinrail`.
- Generic: no employer names, client data or internal specs. Rule packs come from **public** sources only.
- Not certification: the README must say plainly that Twinrail complements scheme testing and does not replace it.
- Scheme names (RTR, FedNow, RTP, SEPA, Swift, NACHA) are used only descriptively, never in the logo or product name.
- Same quality bar as Iris: professional README, docs, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT, CHANGELOG, CI on
  Linux/Windows/macOS, issue templates.

## The gap it fills (summary)

- Tools that understand payments are hosted, need registration or are tied to one vendor's API: Increase,
  Column, Modern Treasury, Payments Canada's RTR sandbox, Swift's Integration Testbed.
- Tools that run locally either know nothing about payments (WireMock, Microcks) or cover one leg of one rail:
  `rseufert/mock-bank` (SEPA + NACHA files, corporate-to-bank only, 1★) and `moov-io/ach-test-harness` (ACH only).
- Nothing local plays the **interbank / scheme side** (`pacs.008` in → `pacs.002` out, `pacs.004` returns,
  `camt.056` recall → `camt.029`), emulates an **instant rail**, ships **scheme rules as data**, or offers a
  **Testcontainers module**. No open tool covers a **Canadian rail**.
- Timing: BetaKit reported RTR Phase 1 rollout begins Nov 9, 2026; Payments Canada plans two later phases to
  migrate Interac e-Transfer volumes and has "no forced deadline" for participants to join.

## Architecture

```
Clients:      system under test · tests (JUnit / pytest / Jest via Testcontainers) · inspector UI
Transports:   REST · webhooks · SFTP / files · MQ / Kafka · control API
Core:         message codec · rule engine · payment lifecycle · virtual clock · ledger · recorder
Data:         rule packs (one per scheme, versioned) · scenario files (match → reject / delay / return / recall)
```

- **Message codec:** ISO 20022 XML and NACHA, built on an existing open library (Prowide ISO 20022 — confirm licence).
- **Rule engine:** checks each message against the active rule pack; rejects with real reason codes; every rule
  tagged with the spec version it came from.
- **Payment lifecycle:** accepted → settled → returned / recalled / timed out; statuses sent asynchronously.
- **Virtual clock:** cut-offs, holidays, time-outs; moved through the control API, never waits.
- **Ledger:** balances per test account; `camt.052/053/054` statements consistent with activity.
- **Recorder:** seeded ids, every message kept, replay and golden files for deterministic CI.
- Design ideas to borrow: mock-bank's per-account behaviours + controllable clock; moov's field-matcher rule files;
  BaaS sandboxes' "simulate this event now" endpoints and webhooks.

## MVP (v0.1)

1. **RTR credit transfer** from Payments Canada's published ISO 20022 specs: `pacs.008` in, `pacs.002`
   accept/reject out (plus settlement commit advice), `pacs.028` status request, `admi.002` reject, heartbeat,
   configurable time-out. Returns and recalls are not in the published RTR set, so they are out of v0.1 for RTR
   (see ADR 0009; findings in `docs/research.md`).
2. **NACHA ACH** with returns and NOCs.
3. **Ledger + statements** (`camt.053`, `camt.054`).
4. **Virtual clock + scenario files.**
5. **REST + webhooks**, a Docker image, and a **Testcontainers module for Java (JUnit 5)**.
6. **Small inspector UI:** message timeline with the rule behind each rejection.

Out of scope for v0.1: SWIFT MT, cards, FX, sanctions screening, performance mode.

## Roadmap

| Release | Adds |
| --- | --- |
| v0.2 | FedNow and RTP rule packs; WireMock extension as an on-ramp |
| v0.3 | SEPA SCT Inst and CBPR+ rule packs; pytest and Jest fixtures |
| v0.4 | SFTP, MQ and Kafka transports; Lynx and AFT for Canada |
| v0.5 | Performance mode for local load tests |

## Open decisions for the kickoff interview

- Language/runtime details: Java 21? Spring Boot or a lighter framework (startup time matters for Testcontainers)?
  GraalVM native image?
- Licence: Apache-2.0 (patent grant, common in Java/enterprise) or MIT (as Iris).
- Rule-pack format (YAML/JSON + JSON Schema?) and how a rule references an ISO 20022 element path.
- Scenario-file format and how it relates to rule packs.
- Control API shape (`/_twinrail/clock/advance`, `/_twinrail/scenarios`, …) and webhook contract.
- Inspector UI: server-rendered or a small SPA bundled in the image.
- Persistence: in-memory only, or optional file snapshot for replay.
- Repo layout: single module vs multi-module (core, rule packs, testcontainers module, UI).

## Open questions to research

1. Is there a FedNow sandbox open to non-participants? (Federal Reserve page could not be read during analysis.)
2. Did RTR Phase 1 go live on Nov 9, 2026, and which messages does the RTR portfolio use for credit transfer,
   return and recall?
3. Terms of use for Payments Canada's published RTR ISO 20022 specifications.
4. Prowide ISO 20022 licence and message-version coverage.

## Go-to-market

- 20-second demo GIF: test sends `pacs.008`, clock jumps, payment times out, inspector shows why.
- Testcontainers module listed in the Testcontainers module catalogue.
- Article timed with RTR rollout: "Testing an RTR integration on your laptop" → Show HN, r/fintech,
  Payments Canada developer community.
- Credit mock-bank and moov in the README; invite their authors to review.
- Before heavy coding: validate demand with 5–10 developers at RTR participants, PSPs and ACH fintechs.
