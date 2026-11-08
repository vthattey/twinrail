# Twinrail

**The offline twin of the payment rails.**

Twinrail is a local emulator for payment rails. It plays the payment scheme and the other bank, speaking real
ISO 20022, so you can develop and test payment integrations on your laptop and in CI, with no sandbox account,
no network and no money.

> **Status: design phase.** Nothing to install yet. Watch the repository to follow progress.

## Planned for v0.1

- An instant credit transfer flow: `pacs.008` in, `pacs.002` accept or reject out, time-outs, `pacs.004`
  returns, and `camt.056` recalls answered with `camt.029`
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

See [docs/brief.md](docs/brief.md).
