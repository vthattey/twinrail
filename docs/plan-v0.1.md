# v0.1 plan

Scope is the MVP in `docs/brief.md` as amended by ADR 0009. Decisions: `docs/decisions/`. Research:
`docs/research.md`. Each milestone ends with tests, docs and a CHANGELOG entry (CLAUDE.md "done").

## Gates before building

- [ ] **G1. Validate demand** with 5–10 developers (RTR participants, PSPs, ACH fintechs) using a README draft and
      the demo storyboard. Output: notes in `docs/validation.md`, no names or employers.
- [ ] **G2. Payments Canada enquiry** (ISO20022@payments.ca): may a public open-source rule pack reference the RTR
      specs? Blocks M5 (RTR pack) only.
- [x] **G3. Spike: Prowide open edition.** Done: classes present, round-trip stable, ~1.4 s cold start (~0.95 s with
      AppCDS); see `docs/research.md` sections 5–6. Image size still to measure in M4 (no Docker yet). Confirm `pacs.008.001.08`, `pacs.002.001.10`, `pacs.028.001.03`,
      `head.001.001.02`, `camt.053`/`.054` exist and (un)marshal; measure image size and startup. Output: update
      ADR 0001 and `docs/research.md`.

## Milestones

### M0. Foundation
- [x] Gradle (Kotlin DSL) multi-module skeleton per ADR 0008; Java 21 toolchain.
- [x] Apache-2.0 LICENSE, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT, CHANGELOG, issue templates.
- [x] CI on Linux, Windows, macOS: build, test, format check. (Green on PR #1.)
- [x] README with the "not certification" statement, trademark note and credits (mock-bank, moov).

### M1. Core model: codec, clock, recorder
- [~] Message codec wrapper: parse, write with pinned settings, golden-file tests, XXE test. Schema validation is
      not done: the Prowide jar ships no XSDs (see `docs/research.md` section 5); decide in M2/M5.
- [x] Virtual clock (advance, set, never sleeps) and seeded id generator.
- [x] Recorder: every message stored; session export/import with a format version (ADR 0007).
- [x] Determinism test: same seed, clock and inputs produce byte-identical output.

### M2. Rule engine and scenarios
- [x] Rule-pack JSON Schema and loader; element-path evaluator (ADR 0003).
- [x] Rejections carry rule id, reason code and source spec/version.
- [x] Scenario schema and matcher shared with rules (ADR 0004); first-match-wins.
- [x] Rule-pack linter: the loader refuses a rule without `source`; a test loads and schema-checks every shipped pack.

### M3. Payment lifecycle and ledger
- [x] State machine: accepted → settled → rejected / timed out (returns/recalls on NACHA only).
- [x] Ledger: balances per test account; `camt.053` and `camt.054` consistent with activity (property tests).
- [x] Time-out driven by the virtual clock.

### M4. Server, control API, webhooks
- [ ] Javalin server; REST message endpoints.
- [ ] `/_twinrail/` control API plus OpenAPI, contract-tested (ADR 0005).
- [ ] Signed webhooks with retry on virtual time; no outbound traffic beyond configured local targets.
- [ ] Dockerfile; image published on tag; startup time recorded in CI.

### M5. RTR pack (after G2)
- [ ] `pacs.008` in → `pacs.002` accept/reject, settlement commit advice, `pacs.028`, `admi.002`, heartbeat, time-out.
- [ ] Rules in own words with ids, paths, codes, spec version (ADR 0003).
- [ ] End-to-end tests including the demo scenario (send, jump clock, time-out).

### M6. NACHA ACH
- [ ] NACHA file codec (own implementation; Prowide does not cover it) with golden files.
- [ ] Returns (R-codes) and NOCs (C-codes) via scenarios; pack with source versions.

### M7. Inspector UI
- [ ] Server-rendered htmx timeline, rule behind each rejection; vendored htmx; escaping tests (ADR 0006).

### M8. Testcontainers module (Java, JUnit 5)
- [ ] Container class, typed client over the control API, example project.
- [ ] Publish to Maven Central; submit to the Testcontainers module catalogue.

### M9. Release v0.1
- [ ] Docs complete: quickstart, rule-pack and scenario guides, API reference, limitations.
- [ ] Demo GIF (send `pacs.008`, clock jump, time-out, inspector shows why).
- [ ] Tag v0.1.0; article draft; Show HN / r/fintech / community posts.

## Order and dependencies

G3 → M0 → M1 → M2 → M3 → M4 → (M5 after G2, M6, M7 in parallel) → M8 → M9. G1 runs alongside M0–M1; if it
finds no demand, stop before M4.

## Risks tracked

- RTR spec reuse terms (G2); RTR launch date unconfirmed.
- Prowide open edition may lack needed versions (G3) or hurt startup.
- NACHA rules come from public sources only; if a needed rule is not public, it is documented as not covered.
