# 0004. Scenario files

Status: accepted (2026-11-08)

## Context
Tests need to make the twin misbehave on purpose: reject, delay, time out, return, recall. That is test behaviour,
not scheme truth.

## Decision
Separate YAML scenario files, validated by their own JSON Schema, reusing the rule-pack matcher syntax for
`match`. Actions: `reject`, `delay`, `drop` (time-out), `return`, `recall`. Loaded at start-up (mounted directory)
or through the control API. Order is explicit; first match wins.

## Consequences
- Rule packs stay pure scheme data; scenarios are per-project.
- The matcher is a shared component in core.
- Which actions apply depends on the scheme's message set (see ADR 0009).
