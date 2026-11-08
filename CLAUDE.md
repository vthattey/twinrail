# Twinrail — instructions for Claude

Twinrail is an open-source, local emulator of payment rails: one Docker image that plays the payment scheme and
the other bank, speaking real ISO 20022, for local development and CI. Read `docs/brief.md` for the full brief
(gap, architecture, MVP, roadmap, risks).

## Current phase: design

No product code exists yet. The next session starts with:

1. An interview (`/grillMe` if available) on the **Open decisions** in `docs/brief.md`: runtime and framework,
   licence, rule-pack and scenario formats, control API, inspector UI, persistence, repo layout.
2. Research of the **Open questions** in `docs/brief.md` (FedNow test access, RTR message set and status,
   Payments Canada spec terms, Prowide ISO 20022 licence). Cite only pages actually read.
3. Record each decision in `docs/decisions/NNNN-title.md` (short ADRs: context, decision, consequences).
4. A v0.1 plan with a task list, then build.

## Ground rules

- **Generic and public.** No employer or client names, internal specs, or real customer data anywhere in the repo,
  commits or issues.
- **Public sources only** for rule packs. Every rule records the spec name and version it came from.
- **Not certification.** Docs must say Twinrail complements scheme and network testing; it does not replace it.
- **Trademarks.** Scheme names (RTR, FedNow, RTP, SEPA, Swift, NACHA, Interac) are used only descriptively,
  never in the product name, logo or package names.
- **No network at runtime.** The emulator never calls out to the internet; tests run offline.
- **Deterministic.** Same inputs, seed and clock produce the same messages and ids.

## Engineering conventions (until ADRs say otherwise)

- Java is the primary language (the maintainer's main stack; Testcontainers and Prowide fit it).
- Keep runtime dependencies few; every new one needs a reason in the PR.
- Scheme behaviour lives in **data** (rule packs, scenario files), not in code branches per scheme.
- Tests for every behaviour change; golden-file tests for generated XML.
- Docs are part of done: README, `docs/`, CHANGELOG (Keep a Changelog), CONTRIBUTING, SECURITY, CODE_OF_CONDUCT,
  CI on Linux, Windows and macOS. Same quality bar as the maintainer's Iris project.
- User-facing errors say what went wrong and what to do next; a rejection always names the rule and reason code.
