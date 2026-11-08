# Contributing to Twinrail

Thanks for helping. Twinrail is an open-source, local emulator of payment rails. Read [docs/brief.md](docs/brief.md)
for the goals and [docs/decisions/](docs/decisions/) for the choices already made.

## Ground rules

- **Generic and public.** No employer or client names, internal specifications or real customer data in code,
  docs, commits or issues.
- **Public sources only for rule packs.** Every rule records the spec name and version it came from, and rule
  packs carry no copied specification text (see [ADR 0003](docs/decisions/0003-rule-pack-format.md)).
- **Not certification.** Twinrail complements scheme and network testing; it does not replace it.
- **Trademarks.** Scheme names are used only descriptively, never in the product name, logo or package names.
- **No network at runtime.** The emulator never calls the internet, and tests run offline.
- **Deterministic.** The same inputs, seed and clock give the same messages and ids.

## Development

You need JDK 21. Docker is needed later for the image and Testcontainers module.

```
./gradlew build          # compile, test, check formatting
./gradlew spotlessApply  # fix formatting
```

## Pull requests

- Scheme behaviour belongs in data (rule packs, scenario files), not in per-scheme code branches.
- Add tests for every behaviour change; use golden files for generated XML.
- Keep runtime dependencies few. A new dependency needs a reason in the PR description.
- Update the docs and the `Unreleased` section of the [CHANGELOG](CHANGELOG.md).
- A rejection must name the rule and reason code, and user-facing errors must say what to do next.
- Significant design choices get a short ADR in `docs/decisions/` (context, decision, consequences).

## Licence

By contributing you agree that your contribution is licensed under the Apache License 2.0, the licence of this
project. Unlike the maintainer's other projects, which use MIT, Twinrail uses Apache-2.0 for its patent grant
([ADR 0002](docs/decisions/0002-licence.md)).
