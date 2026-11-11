# Examples

- `messages/pacs008-credit-transfer.xml`: a credit transfer for the bundled illustrative rule pack.
- `messages/ach-credits.ach`: an ACH file with two credits (JANE DOE and JOHN ROE).
- `scenarios/time-out.yaml`: never answer credit transfers, so the time-out fires.
- `scenarios/ach-return.yaml`: return one ACH entry (R02) and send a change notification (C01) for the other.
- `junit5/`: a standalone JUnit 5 project using the Testcontainers module.

The [quickstart](../docs/quickstart.md) uses them.
