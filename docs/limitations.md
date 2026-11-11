# Limitations

What Twinrail does not do, in one place. If something here would stop you using it, better to know now.

## It is not certification, and not a scheme

Twinrail complements scheme and network testing; it does not replace it. It helps you arrive at that testing with
most integration bugs already fixed. Nothing it does is evidence of compliance with any scheme. Scheme names are
used only to describe message compatibility.

## Rails and rule packs

- **There is no Real-Time Rail pack yet.** The plan for it (ADR 0009) is to follow the message set that Payments
  Canada publishes (`pacs.008` in, `pacs.002` accept or reject, settlement advice, `pacs.028`, `admi.002`,
  heartbeat, time-out). It is blocked on confirming that a public open-source rule pack may reference those
  specifications. Until then:
  - the bundled rule pack is an **illustrative example, not a scheme**, and says so;
  - a `pacs.008` is answered with **JSON, not a `pacs.002`**; there is no `pacs.028`, `admi.002` or heartbeat
    handling;
  - returns and recalls are not in the published RTR message set, so they are not planned for it in v0.1.
- Only `pacs.008` starts a payment. Other ISO 20022 types are refused with a message saying so.
- **No schema validation of XML.** The ISO 20022 library the codec uses ships no XSDs. Messages are parsed and the
  rule pack's checks run; a document that parses but would fail the official XSD is not caught.
- Other rails (FedNow, SEPA, Interac) are not covered. New rails are meant to arrive as rule packs.

## ACH

- The ACH record layouts and the return and change code tables come from **secondary sources**, not Nacha's own
  rules, which were not read ([research.md](research.md), section 7). Check them against Nacha's documents before
  relying on them.
- Return and change codes are limited to what those sources list: R01–R39 and R45; C01–C03, C05–C09, C13, C14
  (scenarios can use C01–C03, C05–C07, C09). C04, C10–C12 and later return codes are absent.
- Returns and changes are reported as data plus the addenda record. **No return or change entry file is generated**,
  because the fields of those entries are not in any source read.
- Not modelled: the routing-number check digit, which codes apply to which SEC codes, return deadlines as enforced
  rules (the time frame is shown, not enforced), IAT, prenote rules, effective-date and settlement-date handling.
- No webhook events for ACH entries.

## Behaviour of the emulator

- **State is in memory.** A restart loses it. Export a session (`GET /_twinrail/session`) to keep a record; importing
  one is a file-format feature of the library but there is no import endpoint yet.
- **One virtual clock, moved only by you.** Time never goes backwards; use `reset` to start again.
- **Accounts are forgiving by default.** An account a payment mentions but nobody opened is created on first use
  with a zero balance and may go negative. Accounts you open cannot be overdrawn. There is one currency per account.
- A payment's time-out is a test setting (`TWINRAIL_TIMEOUT`, default 30 s), not a scheme value.
- **Webhooks:** local targets only (the emulator never contacts the internet); retries happen when the clock moves,
  not in real time; delivery history shows current state only.
- `camt.053` and `camt.054` are generated from the ledger. Balance dates use the time the statement was produced,
  and the bank transaction code is a generic payments code.
- Duplicate detection, cut-off times, holidays and fees are not modelled.

## Security and operation

- **No authentication.** It is a local development tool; do not expose its port to a network you do not trust.
- The inspector UI polls every two seconds, has no search or filtering, and no per-payment page.
- Single process, one lock: it is meant for tests, not load testing.

## Distribution

- The container image and the Maven module are built and tested in CI but **not yet published**; the first
  release has not been tagged.
- Startup time on a developer laptop was several seconds before the class-data archive and a few seconds after (see
  [docker.md](docker.md)); CI records the Linux figure.
