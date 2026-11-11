# v0.1 plan

Scope is the MVP in `docs/brief.md` as amended by ADR 0009. Decisions: `docs/decisions/`. Research:
`docs/research.md`. Each milestone ends with tests, docs and a CHANGELOG entry (CLAUDE.md "done").

## Gates before building

- [ ] **G1. Validate demand** with 5–10 developers (RTR participants, PSPs, ACH fintechs) using a README draft and
      the demo storyboard. Output: notes in `docs/validation.md`, no names or employers.
      Method, questions and decision rule are in `docs/validation.md`; no conversations held yet.
- [ ] **G2. Payments Canada enquiry** (ISO20022@payments.ca): may a public open-source rule pack reference the RTR
      specs? Blocks M5 (RTR pack) only.
      Enquiry in `docs/g2-enquiry-draft.md` **sent 2026-11-11**; awaiting reply (follow-up planned around 2026-11-25).
      **Decision 2026-11-10: the v0.1 release waits for the answer**; no `v0.1.0` tag until then.
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
- [x] Javalin server; REST message endpoints.
- [x] `/_twinrail/` control API plus OpenAPI, contract-tested (ADR 0005).
- [x] Signed webhooks with retry on virtual time; no outbound traffic beyond configured local targets.
- [~] Dockerfile; image published on tag; startup time recorded in CI. Written (`docs/docker.md`) but not yet run:
      no Docker locally, so the first CI run is its first test. Distribution and AppCDS archive verified outside Docker.

### M5. RTR pack (after G2)
- [ ] `pacs.008` in → `pacs.002` accept/reject, settlement commit advice, `pacs.028`, `admi.002`, heartbeat, time-out.
- [ ] Rules in own words with ids, paths, codes, spec version (ADR 0003).
- [ ] End-to-end tests including the demo scenario (send, jump clock, time-out).

### M6. NACHA ACH
- [x] NACHA file codec (own implementation; Prowide does not cover it) with golden files. (`docs/nacha.md`;
      layouts from secondary sources, see `docs/research.md` section 7.)
- [x] Returns (R-codes) and NOCs (C-codes) via scenarios; code table with a source on every code (secondary
      sources; limits in `docs/nacha.md`: no return/change entry file, no webhook events yet).

### M7. Inspector UI
- [x] Server-rendered htmx timeline, rule behind each rejection; vendored htmx; escaping tests (ADR 0006).
      (`docs/ui.md`; checked in a real browser: htmx runs under the strict CSP, rows stay open across refreshes.)

### M8. Testcontainers module (Java, JUnit 5)
- [x] Container class, typed client over the control API, example project. (`docs/testcontainers.md`; the
      client is tested against the real server, the container class without Docker; the real-image test and
      the example run in CI against the built image and had not run when this was written.)
- [ ] Publish to Maven Central; submit to the Testcontainers module catalogue. Build, POM, sources and javadoc
      jars and signing are set up; the namespace, tokens, upload and catalogue submission are the maintainer's
      (steps in `docs/testcontainers.md`).

### M9. Release v0.1
- [x] Docs complete: quickstart, rule-pack and scenario guides, API reference, limitations. (Done for what
      exists: `docs/quickstart.md` was run command by command; `docs/limitations.md` lists the gaps, including
      that the RTR pack (M5, blocked on G2) does not exist, so the demo and README describe JSON answers, not
      `pacs.002`.)
- [x] Demo GIF (send `pacs.008`, clock jump, time-out, inspector shows why). (`docs/media/twinrail-demo.gif`:
      rejection with its rule, then a time-out by clock jump. It shows JSON answers, not `pacs.002`; re-record
      when the RTR pack (M5) exists.)
- [ ] Tag v0.1.0; article draft; Show HN / r/fintech / community posts.

## Order and dependencies

G3 → M0 → M1 → M2 → M3 → M4 → (M5 after G2, M6, M7 in parallel) → M8 → M9. G1 runs alongside M0–M1; if it
finds no demand, stop before M4.

## Risks tracked

- RTR spec reuse terms (G2); RTR launch date unconfirmed.
- Prowide open edition may lack needed versions (G3) or hurt startup.
- NACHA rules come from public sources only; if a needed rule is not public, it is documented as not covered.
