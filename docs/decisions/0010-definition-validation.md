# 0010. How rule packs and scenarios are validated

Status: accepted (2026-11-08)

## Context
ADR 0003 says rule packs are "validated by a published JSON Schema". Scenario files (ADR 0004) need the same. Users
also need errors that name the rule and say what to change, which generic schema errors do not do well. A runtime
JSON Schema validator pulls in a large dependency tree (JSON and YAML libraries), and CLAUDE.md asks for few runtime
dependencies.

## Decision
- The JSON Schemas are published in `rule-packs/src/main/resources/schemas/` for editors and CI.
- At run time a strict hand-written loader (on top of SnakeYAML Engine) validates and reads both formats. It rejects
  unknown keys, duplicate keys, missing sources, bad paths and bad regular expressions, with messages that name the
  file and the item.
- A test feeds the same corpus of valid and invalid documents to the schema and the loader and requires the same
  verdict. The validator library is a test-only dependency.
- Paths use a restricted XPath subset, translated to JDK XPath with `local-name()` so namespaces are ignored.

## Consequences
- One runtime dependency (SnakeYAML Engine) instead of a JSON Schema stack.
- Two descriptions of the format (schema and loader) that can drift; the agreement test is what prevents it, so new
  fields must be added to both and to the corpus.
- Some checks cannot be expressed in a schema (duplicate ids, regex syntax); only the loader enforces them.
- The loader is the authority when the two differ.
