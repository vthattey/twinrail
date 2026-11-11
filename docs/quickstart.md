# Quickstart

Ten minutes from nothing to a payment that times out on a virtual clock, with the inspector showing why.

Twinrail complements scheme and network testing; it does not replace it. Read [limitations](limitations.md) first
if you are deciding whether it fits.

## 1. Start it

You need JDK 21.

```sh
./gradlew :server:installDist
server/build/install/twinrail/bin/twinrail      # listens on http://localhost:8080
```

On Windows use `server\build\install\twinrail\bin\twinrail.bat`. Settings are environment variables
(`TWINRAIL_PORT`, `TWINRAIL_SEED`, `TWINRAIL_START`, `TWINRAIL_TIMEOUT`, `TWINRAIL_RULE_PACK`); see
[control-api.md](control-api.md).

With Docker instead: `docker build -t twinrail .` then `docker run --rm -p 8080:8080 twinrail`. (The image build is
checked in CI; it has not been run on the maintainer's machine, which has no Docker. See [docker.md](docker.md).)

Open <http://localhost:8080/ui> in a browser: that is the [inspector](ui.md). Leave it open.

```sh
curl http://localhost:8080/_twinrail/health
# {"status":"ok"}
```

## 2. Send a credit transfer

The files below are in [`examples/`](../examples/README.md). The bundled rule pack is an **illustrative example, not
a scheme** (`docs/rule-packs.md`).

```sh
curl -X POST http://localhost:8080/messages --data-binary @examples/messages/pacs008-credit-transfer.xml
```

```json
{"id":"PMT-000001","state":"SETTLED","endToEndId":"E2E-1","debtorAccount":"000000001","creditorAccount":"000000002",
 "amount":"125.50","currency":"CAD","receivedAt":"2026-01-01T00:00:00Z", ...}
```

The time is virtual: the clock starts at `2026-01-01T00:00:00Z` and only moves when you move it.

## 3. See a rejection name its rule

```sh
sed 's/125.50</0.00</' examples/messages/pacs008-credit-transfer.xml \
  | curl -X POST http://localhost:8080/messages --data-binary @-
```

The answer has status 422 and says which rule failed, its reason code and where the rule comes from:

```json
{"id":"PMT-000002","state":"REJECTED", ... ,
 "rejection":{"rule":"EX-002","reason":"AM01","text":"The settlement amount must be greater than zero.",
              "source":"Twinrail example pack 0.1, amount"}}
```

## 4. Make a payment time out, without waiting

```sh
curl -X POST http://localhost:8080/_twinrail/reset
curl -X PUT  http://localhost:8080/_twinrail/scenarios --data-binary @examples/scenarios/time-out.yaml
curl -X POST http://localhost:8080/messages --data-binary @examples/messages/pacs008-credit-transfer.xml
# {"id":"PMT-000001","state":"ACCEPTED", ...}   accepted, but never answered

curl -X POST http://localhost:8080/_twinrail/clock/advance -d '{"by":"PT30S"}'
# {"now":"2026-01-01T00:00:30Z","transitions":[{"paymentId":"PMT-000001","from":"ACCEPTED","to":"TIMED_OUT", ...}]}
```

No sleeping: the 30 seconds were virtual. In the inspector, the `PMT-000001 timed out` row appears at the time it was
due. The +30 s button does the same as the last command.

## 5. ACH: a return and a change notification

```sh
curl -X POST http://localhost:8080/_twinrail/reset
curl -X PUT  http://localhost:8080/_twinrail/scenarios --data-binary @examples/scenarios/ach-return.yaml
curl -X POST http://localhost:8080/ach/files --data-binary @examples/messages/ach-credits.ach
curl -X POST http://localhost:8080/_twinrail/clock/advance -d '{"by":"PT1H"}'
curl http://localhost:8080/_twinrail/ach/entries/ACH-000001
```

The first entry is returned `R02` an hour after it settled (the response shows the code's title, the return time frame
from the code table's source, and the 94-character addenda record); the second carries a `C01` change notification.
A file whose control totals are wrong is refused whole with a 422 and the problem ids. See [nacha.md](nacha.md),
including what the code table is based on.

## 6. Accounts and statements

```sh
curl -X POST http://localhost:8080/_twinrail/accounts -d '{"id":"000000001","currency":"CAD","balance":"500.00"}'
curl http://localhost:8080/_twinrail/accounts/000000001/statement      # camt.053.001.08
```

## 7. From a test

The [Testcontainers module](testcontainers.md) starts the container and gives you a typed client for everything
above; [`examples/junit5`](../examples/junit5/README.md) is a working project.

## Next

- [Scenario files](scenarios.md): scripted failures. [Rule packs](rule-packs.md): scheme behaviour as data.
- [Control API](control-api.md) and its [OpenAPI file](../server/src/main/resources/openapi/control-1.yaml).
- [Webhooks](control-api.md#webhooks), signed, with retries on virtual time.
- [Payment lifecycle and ledger](lifecycle.md).
