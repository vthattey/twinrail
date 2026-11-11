# Scenario files

Rule packs say what is valid for a scheme. A scenario file says how the twin should **behave** for matching
messages in your tests: reject, answer late, or never answer. The format is described by
[`scenario-1.schema.json`](../rule-packs/src/main/resources/schemas/scenario-1.schema.json).

## Example

```yaml
format: 1
scenarios:
  - id: closed-account
    description: Creditor account is closed
    match:
      message: pacs.008.001.08
      where:
        - { path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/Cdtr/Nm, equals: "Closed Corp" }
    action: { type: reject, reason: AC04, text: Account closed }

  - id: slow-bank
    match:
      where:
        - { path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/Cdtr/Nm, equals: "Slow Corp" }
    action: { type: delay, duration: PT30S }

  - id: black-hole
    match:
      where:
        - { path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/IntrBkSttlmAmt, min: "1000" }
    action: { type: drop }
```

## Matching

- Scenarios are checked **in file order and the first match wins**. A scenario with no `match` block matches
  every message, so put it last.
- `match.message` limits a scenario to one message type; leave it out to match any.
- `match.where` is a list of matchers (see [rule packs](rule-packs.md#matchers)); **all** must select the
  message. A missing element never selects, except with `absent: true`.

## Actions

| `type` | Fields | Effect |
| --- | --- | --- |
| `reject` | `reason` (required), `text` | Answer with a rejection carrying the reason code. |
| `delay` | `duration` (ISO 8601, greater than zero, such as `PT30S`) | Answer after that much **virtual** time. |
| `drop` | none | Never answer, so the sender's time-out fires. |

Return and recall actions arrive with the rails that support them
([ADR 0009](decisions/0009-v01-rtr-scope-follows-published-message-set.md)).

Scenario files are loaded strictly, like rule packs: unknown fields, unknown action types and duplicate ids are
errors that say what to change.

> Status: the loader, matching and the payment lifecycle ([lifecycle](lifecycle.md)) are implemented in `core`.
> Wiring scenarios to the control API (M4) is not done yet.
