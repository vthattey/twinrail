# Server and control API

The server speaks JSON over HTTP, except statements, which are ISO 20022 XML. Errors are
`{"error": "<what went wrong and what to do>"}` with status 400 (bad input), 404 (not found) or 500 (a bug; please
report it).

> Status: M4 in progress. The OpenAPI file and contract tests and the Docker image are not done yet. Until the OpenAPI file exists, treat this page as the contract, and expect changes before v0.1.

## Starting

`Main` reads environment variables: `TWINRAIL_PORT` (8080), `TWINRAIL_SEED` (1), `TWINRAIL_START`
(`2026-01-01T00:00:00Z`, the virtual clock's start), `TWINRAIL_TIMEOUT` (`PT30S`) and `TWINRAIL_RULE_PACK` (a pack
file; default is the bundled illustrative example pack, which is not a scheme).

## Messages

`POST /messages` with a `pacs.008` XML body runs the payment ([lifecycle](lifecycle.md)) and returns the payment.
Status 200 if accepted or settled, 422 if rejected; a rejection carries `rule`, `reason`, `text` and `source`.
This is a JSON view for now; scheme-specific replies such as `pacs.002` arrive with the rule packs (M5).

## Control API, under `/_twinrail`

| Method and path | Body | Effect |
| --- | --- | --- |
| `GET /health` | | `{"status":"ok"}` |
| `POST /reset` | | Back to the start: clock, ids, ledger, payments, scenarios, recording. 204. |
| `GET /clock` | | Current virtual time. |
| `POST /clock/advance` | `{"by":"PT30S"}` | Moves time forward, applies what became due, returns the transitions. |
| `POST /clock/set` | `{"to":"2026-11-09T10:05:00Z"}` | Same, to an instant. Time never moves back. |
| `POST /accounts` | `{"id","currency","balance"}` | Opens an account that cannot be overdrawn. 201. |
| `GET /accounts`, `GET /accounts/{id}` | | Balance and available funds. |
| `GET /accounts/{id}/statement` | | `camt.053.001.08` XML. |
| `PUT /scenarios` | scenario YAML | Replaces the scenarios for payments received from now on. |
| `GET /scenarios` | | Ids of the loaded scenarios. |
| `GET /payments`, `GET /payments/{id}` | | Payment state, times and any rejection. |
| `POST /webhooks` | `{"url","secret"}` | Registers a webhook target. 201. See below. |
| `GET /webhooks`, `DELETE /webhooks/{id}` | | List targets (never the secret) or remove one. |
| `GET /webhooks/deliveries` | | Every delivery: state, attempts, last status, next attempt time. |
| `GET /session` | | The recorded session as a session file (ADR 0007). |

Everything runs on the virtual clock: the server never waits, and the same seed, start time and calls give the
same responses.

## Webhooks

Each payment state change sends one event to every registered target: `payment.accepted`, `payment.settled`,
`payment.rejected` or `payment.timed_out`. The body is a fixed envelope:

```json
{"id": "EVT-000002", "type": "payment.settled", "time": "2026-11-09T10:00:00Z", "payload": { "...payment..." }}
```

`time` is the virtual time of the change, and `payload` is the payment as the REST API shows it, with `state` set
to the state at that event. Headers: `Twinrail-Event-Id` and `Twinrail-Signature: sha256=<hex>`, the HMAC-SHA256 of
the exact body using the target's secret. Compute it over the raw bytes you received and compare in constant time.

**Retries run on virtual time.** A target that answers anything but 2xx, or cannot be reached, gets the event again
when the clock reaches the next attempt: 1 s, 2 s, 4 s, then 8 s after the previous attempt, five attempts in all,
then the delivery is `FAILED`. Nothing waits in real time: a retry happens on the next clock move
(`/clock/advance` or `/clock/set`) that reaches its time. Delivery order within one request follows event order.

**Local targets only.** The emulator never sends traffic to the internet, so a target must be `localhost`, a
loopback or private-range IP address, `host.docker.internal`, or a single-label host name such as a Docker network
alias. Anything else is refused with a 400 that says so.
