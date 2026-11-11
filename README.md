# Twinrail

**The offline twin of the payment rails.**

Twinrail is a local emulator for payment rails. It plays the payment scheme and the other bank, speaking real
ISO 20022, so you can develop and test payment integrations on your laptop and in CI, with no sandbox account,
no network and no money.

> **Status: design phase.** Nothing to install yet. Watch the repository to follow progress.

## Planned for v0.1

- An instant credit transfer flow based on Payments Canada's published Real-Time Rail message set: `pacs.008` in,
  `pacs.002` accept or reject out, `pacs.028` status requests, `admi.002` rejects and time-outs (returns and
  recalls are not part of the published set, so they are not in v0.1 for this rail; see
  [ADR 0009](docs/decisions/0009-v01-rtr-scope-follows-published-message-set.md))
- ACH files with returns and notifications of change
- Balances per test account, with `camt.053` and `camt.054` statements
- A virtual clock for cut-offs, holidays and time-outs, and scenario files for scripted failures
- REST and webhooks, a Docker image, and a Testcontainers module for JUnit 5
- A small inspector UI that shows every message and the rule behind each rejection

Scheme behaviour lives in versioned **rule packs**, so new rails are added as data.

## What Twinrail is not

Twinrail does not certify anything. It helps you arrive at scheme and network testing with most bugs already
fixed; it does not replace that testing. Scheme names are used only to describe message compatibility.

## Design

See [docs/brief.md](docs/brief.md), the [v0.1 plan](docs/plan-v0.1.md), the [research notes](docs/research.md) and
the [decision records](docs/decisions/).

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
(field-matcher rule files). Thank you to their authors.

## Licence

[Apache License 2.0](LICENSE).
