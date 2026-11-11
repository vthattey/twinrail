# Server and control API

The server speaks JSON over HTTP, except statements, which are ISO 20022 XML. Errors are
`{"error": "<what went wrong and what to do>"}` with status 400 (bad input), 404 (not found) or 500 (a bug; please
report it).

> Status: first slice of M4. The OpenAPI file and contract tests, signed webhooks and the Docker image are not done
> yet. Until the OpenAPI file exists, treat this page as the contract, and expect changes before v0.1.

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
| `GET /session` | | The recorded session as a session file (ADR 0007). |

Everything runs on the virtual clock: the server never waits, and the same seed, start time and calls give the
same responses.
