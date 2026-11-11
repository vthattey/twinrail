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
| `return` | `code` (R-code, required), `after` (ISO 8601, zero or more, default none), `text` | ACH only: return the entry with that return code, `after` it settles. |
| `noc` | `code` (C-code) and the corrected values it needs | ACH only: notify of a change for a settled entry. |

### ACH scenarios

Match ACH entries with `message: nacha.entry` and paths into the entry view described in
[ADR 0011](decisions/0011-ach-scenarios-and-outputs.md): `/Entry/TransactionCode`, `RoutingNumber`, `AccountNumber`,
`Amount` (decimal), `IndividualId`, `IndividualName`, `TraceNumber`, `SecCode`, `CompanyName`, `CompanyId`,
`EntryDescription`, `EffectiveEntryDate`.

```yaml
format: 1
scenarios:
  - id: closed-account
    match:
      message: nacha.entry
      where: [{ path: /Entry/IndividualName, equals: "JANE DOE" }]
    action: { type: return, code: R02 }
  - id: moved-bank
    match:
      message: nacha.entry
      where: [{ path: /Entry/AccountNumber, equals: "987654321" }]
    action: { type: noc, code: C01, account: "55555555" }
```

Supported change codes and the values each needs: C01 `account`; C02 `routing`; C03 `routing`, `account`; C05
`transactionCode`; C06 `account`, `transactionCode`; C07 `routing`, `account`, `transactionCode`; C09
`individualId`. Return codes are the R-codes in the shipped table (R01–R39 and R45). `reject` and `drop` do not
apply to ACH entries. Code tables come from a secondary source; see [nacha.md](nacha.md).

Return and recall actions arrive with the rails that support them
([ADR 0009](decisions/0009-v01-rtr-scope-follows-published-message-set.md)).

Scenario files are loaded strictly, like rule packs: unknown fields, unknown action types and duplicate ids are
errors that say what to change.

> Status: the loader, matching and the payment lifecycle ([lifecycle](lifecycle.md)) are implemented in `core`.
> Wiring scenarios to the control API (M4) is not done yet.
