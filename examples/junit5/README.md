# Twinrail JUnit 5 example

A standalone project that uses the Twinrail Testcontainers module the way your own tests would: one container per
test class, `reset()` before each test, and the clock moved by the test instead of waiting.

```sh
# From the repository root: build the image once, then run the example against it.
docker build -t twinrail .
TWINRAIL_IMAGE=twinrail ./gradlew -p examples/junit5 test
```

Until the module is on Maven Central, publish it locally first (`./gradlew :testcontainers:publishToMavenLocal`);
the example then resolves it from your local Maven repository. With a released module and image, drop
`TWINRAIL_IMAGE`: `new TwinrailContainer()` uses the image that matches the module version.

The tests show a settled transfer, a rejection that names its rule and reason code, and a time-out reached by
moving the virtual clock. Twinrail complements scheme and network testing; it does not replace it.
