# Testcontainers module

`io.github.vthattey.twinrail:twinrail-testcontainers` starts the Twinrail emulator in a
[Testcontainers](https://testcontainers.com) container and drives it through a typed Java client. It works with
JUnit 5 (or anything else: it is a plain `GenericContainer`). Java 21, Testcontainers 2.0.

> Status: written and tested, **not yet published**. The client is tested against the real server in process; the
> container class is tested without Docker; the test that starts the real image runs in CI once the image is built
> (it had not run when this page was written, because there is no Docker on the development machine). See
> "Publishing" for what is left.

## Use

```java
@Testcontainers
class PaymentFlowTest {
  @Container
  static final TwinrailContainer TWINRAIL = new TwinrailContainer();

  @Test
  void aPaymentThatIsNeverAnsweredTimesOut() {
    TwinrailClient twinrail = TWINRAIL.client();
    twinrail.reset();
    twinrail.setScenarios("format: 1\nscenarios:\n  - id: hole\n    action: { type: drop }\n");

    var payment = twinrail.sendMessage(creditTransferXml);
    twinrail.advance(Duration.ofSeconds(30));          // virtual time: no sleeping

    assertEquals(PaymentState.TIMED_OUT, twinrail.payment(payment.id()).state());
  }
}
```

A complete project is in [`examples/junit5`](../examples/junit5/README.md).

## The container

`new TwinrailContainer()` uses the image that matches the module version (`ghcr.io/vthattey/twinrail:<version>`);
`new TwinrailContainer("my-image")` uses another. A snapshot build of the module has no published image, so build
one (`docker build -t twinrail .`) and pass its name.

| Method | Effect |
| --- | --- |
| `withSeed(long)` | Seed for generated ids. Same seed, start time and calls give the same results. |
| `withStart(Instant)` | Where the virtual clock starts. |
| `withPaymentTimeout(Duration)` | How long an accepted payment may stay unsettled (virtual time). |
| `withRulePack(MountableFile)` / `withRulePack(String)` | Use your own rule pack instead of the bundled illustrative one. |
| `baseUrl()` / `client()` | Where it listens, and a `TwinrailClient` for it. |

It is ready when `GET /_twinrail/health` answers. Start one container per test class and call `reset()` before each
test: that is much faster than a container per test.

## The client

`TwinrailClient` wraps the [control API](control-api.md) with typed results: `sendMessage`, `payments`, `payment`,
`openAccount`, `account`, `accounts`, `statement`, `setScenarios`, `advance`, `setClock`, `now`, `sendAchFile`,
`achEntries`, `achEntry`, `registerWebhook`, `webhooks`, `removeWebhook`, `deliveries`, `session`, `reset`,
`isHealthy`. It can also be used against an emulator you started yourself: `new TwinrailClient(URI.create("http://localhost:8080"))`.

- A **rejected** payment or ACH file is a result, not an exception: check `payment.isRejected()` and
  `payment.rejection()` (rule, reason code, text, source), or `AchFileResult.problems()`.
- A request the emulator **refuses** (bad XML, unknown account, non-local webhook target, a clock moved backwards)
  throws `TwinrailException` with the emulator's own explanation and the HTTP status; `-1` means it could not be
  reached.
- Time is virtual. Nothing becomes due until you call `advance` or `setClock`, which also send due webhooks.

## Publishing

The module builds a sources jar, a javadoc jar and a POM with licence, SCM and developer details
(`./gradlew :testcontainers:publishToMavenLocal` produces them), and signs the publication when `SIGNING_KEY` and
`SIGNING_PASSWORD` are set in the environment. Still to do, **by the maintainer**, and not yet exercised:

1. Claim and verify the `io.github.vthattey` namespace with Maven Central (the Central Portal).
2. Create a publishing token and a signing key, and store them as repository secrets.
3. Upload a release of the module (version without `-SNAPSHOT`) and publish it. Check Sonatype's current
   documentation for the upload method; it has not been tried here.
4. Tag the release so the image is published with the same version (`release-image.yml`), since the default image
   tag is the module version.
5. Submit the module to the Testcontainers module catalogue. Its process has not been researched yet.
