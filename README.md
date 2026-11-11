# Twinrail

**The offline twin of the payment rails.**

Twinrail is a local emulator for payment rails. It plays the payment scheme and the other bank, so you can develop
and test payment integrations on your laptop and in CI, with no sandbox account, no network and no money. Time is
virtual, so a time-out, a return or a cut-off takes one API call instead of a wait.

> **Status: pre-release (v0.1 in progress).** It builds and its tests pass, but nothing is published yet: no
> release tag, no container image on a registry, no Maven artifact. Build it from source with the
> [quickstart](docs/quickstart.md). Read the [limitations](docs/limitations.md) before deciding whether it fits.

## What works today

- **ISO 20022 credit transfers.** Send a `pacs.008`; it is checked against a rule pack, settled on a ledger, rejected
  with the **rule id, reason code and source** that caused it, or left pending until a time-out. The bundled rule
  pack is an illustrative example, **not a scheme**.
- **ACH files.** Entries settle, a debit the account cannot cover is returned R01, and scenarios drive returns
  (R-codes) and notifications of change (C-codes). Files with wrong control totals are refused with the reason.
  (Layouts and code tables come from secondary sources; see the [ACH notes](docs/nacha.md).)
- **A virtual clock.** Advance it and everything that became due happens, in order, at the time it was due.
- **Accounts and statements.** Balances per test account, with `camt.053` and `camt.054` generated from the ledger.
- **Scenario files.** Script failures per message: reject, delay, drop (time out), return, notify of change.
- **REST control API and signed webhooks**, with an [OpenAPI file](server/src/main/resources/openapi/control-1.yaml)
  that a contract test keeps honest. Webhook retries also run on virtual time.
- **An inspector UI** at `/ui`: a live timeline of messages and state changes with the rule behind each rejection.
- **A Dockerfile and a Testcontainers module** for JUnit 5, with a [standalone example](examples/junit5/README.md).
- **Deterministic:** the same seed, start time and requests give byte-identical responses, checked across separate
  JVM launches in CI.

## What does not exist yet

- **The Real-Time Rail pack.** The plan (ADR 0009) is to follow the message set Payments Canada publishes, with
  `pacs.002` replies, `pacs.028`, `admi.002` and time-outs. It is waiting on confirmation that a public open-source
  rule pack may reference those specifications. Until then a `pacs.008` is answered in JSON, not as a `pacs.002`.
- Other rails (FedNow, SEPA, Interac). New rails are meant to arrive as rule packs, not code.
- Published artifacts. See the [plan](docs/plan-v0.1.md) for what is left before v0.1.

## Try it

```sh
./gradlew :server:installDist
server/build/install/twinrail/bin/twinrail
curl -X POST http://localhost:8080/messages --data-binary @examples/messages/pacs008-credit-transfer.xml
```

Open <http://localhost:8080/ui>. The [quickstart](docs/quickstart.md) continues with a rejection, a time-out reached by
moving the clock, and an ACH return.

## What Twinrail is not

Twinrail does not certify anything. It helps you arrive at scheme and network testing with most bugs already
fixed; it does not replace that testing. Scheme names are used only to describe message compatibility.

## Documentation

[Quickstart](docs/quickstart.md) · [Limitations](docs/limitations.md) · [Control API](docs/control-api.md) ·
[Scenarios](docs/scenarios.md) · [Rule packs](docs/rule-packs.md) · [Lifecycle and ledger](docs/lifecycle.md) ·
[ACH](docs/nacha.md) · [Inspector UI](docs/ui.md) · [Docker](docs/docker.md) ·
[Testcontainers](docs/testcontainers.md)

Design: [brief](docs/brief.md), the [v0.1 plan](docs/plan-v0.1.md), the [research notes](docs/research.md) and the
[decision records](docs/decisions/).

## Building

You need JDK 21.

```
./gradlew build
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Please read the [code of conduct](CODE_OF_CONDUCT.md); report security
issues as described in [SECURITY.md](SECURITY.md).

## Credits

Twinrail borrows ideas from [`rseufert/mock-bank`](https://github.com/rseufert/mock-bank) (per-account behaviours
and a controllable clock) and [`moov-io/ach-test-harness`](https://github.com/moov-io/ach-test-harness)
(field-matcher rule files). Thank you to their authors. The inspector UI vendors [htmx](https://htmx.org) (Zero-Clause
BSD).

## Licence

[Apache License 2.0](LICENSE).
