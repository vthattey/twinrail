# 0003. Rule-pack format

Status: accepted (2026-11-08)

## Context
Scheme behaviour must live in data, and every rule must record its source spec and version. The RTR companion
document carries a notice restricting use "for any other purpose" without Payments Canada's consent
(see `docs/research.md`, section 3).

## Decision
- One YAML file per scheme and version, validated by a published JSON Schema.
- A rule has: `id`, `source` (spec name, version, section), `path` (XPath-like element path into the ISO 20022
  message), `when`/`assert` condition, `reason` code and a short message in our own words, and `severity`.
- The engine rejects with the rule id and reason code, as required by CLAUDE.md.
- Rule packs contain **no copied spec text, tables or schemas**. They hold only ids, paths, codes and our own
  wording, until Payments Canada confirms reuse terms.

## Consequences
- Packs are easy to review and diff; contributors do not need to write Java.
- XPath-like paths need a small evaluator over the Prowide model or the raw XML (decide in the codec task).
- Reuse risk for RTR is open; the first RTR pack ships only after the enquiry to ISO20022@payments.ca is answered
  or the minimal form above is judged safe.

See also [ADR 0010](0010-definition-validation.md) for how packs are validated at run time and in CI.
