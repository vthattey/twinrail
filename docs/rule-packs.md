# Rule packs

A rule pack is a YAML file that says what a valid message looks like for one scheme or profile. Scheme behaviour
lives here, not in code. The format is described by
[`rule-pack-1.schema.json`](../rule-packs/src/main/resources/schemas/rule-pack-1.schema.json).

> The only pack shipped today is [`example.yaml`](../rule-packs/src/main/resources/packs/example.yaml). It
> illustrates the format and is **not** the rule set of any scheme. Twinrail complements scheme and network
> testing; it does not replace it.

## Example

```yaml
format: 1
pack:
  id: example
  name: Example pack (illustrative, not a scheme)
  version: "0.1"
rules:
  - id: EX-002
    message: pacs.008.001.08
    source: { spec: "Twinrail example pack", version: "0.1", section: "amount" }
    assert: { path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/IntrBkSttlmAmt, min: "0.01" }
    reason: AM01
    text: The settlement amount must be greater than zero.
```

## Rule fields

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | yes | Unique within the pack. Letters, digits, `.`, `_`, `-`. |
| `message` | yes | The message identifier the rule applies to, like `pacs.008.001.08`. |
| `source` | yes | `spec` and `version` of the **public** specification the rule comes from, and optionally `section`. A rule without a source is rejected when the pack loads. |
| `when` | no | A list of selectors. The rule only applies if all of them select the message. |
| `assert` | yes | A matcher that must hold when the rule applies. |
| `reason` | yes | The reason code reported on rejection, 2 to 4 capital letters or digits (for example an ISO 20022 external status reason code). |
| `text` | yes | What is wrong, in your own words. |
| `severity` | no | `error` (default) rejects the message. `warning` reports it but accepts the message. |

**Do not paste specification text, tables or schemas into a pack.** Write the rule in your own words and cite the
source. Some specifications restrict reuse; see [research notes](research.md) section 3 and
[ADR 0003](decisions/0003-rule-pack-format.md).

## Matchers

A matcher is `{ path: ..., <condition>: <value> }` with exactly one condition. Rule packs and
[scenario files](scenarios.md) use the same syntax.

| Condition | Value | Holds when |
| --- | --- | --- |
| `present` | `true` | the path selects at least one node |
| `absent` | `true` | the path selects nothing |
| `equals` / `notEquals` | quoted text | every selected value is (not) equal |
| `oneOf` | list of quoted text | every selected value is in the list |
| `matches` | quoted regex | every selected value matches the whole pattern |
| `maxLength` / `minLength` | whole number | every selected value has at most / at least that many characters |
| `min` / `max` | whole number or quoted decimal | every selected value is a number at or above / below the bound |

Quote text values. YAML turns an unquoted `1.50` into the number 1.5, so `equals: 1.50` is refused. Write
`equals: "1.50"`.

### Paths

Paths are a small, safe subset of XPath: absolute, steps separated by `/`, each step an element name or `*`,
optionally with a 1-based index (`CdtTrfTxInf[2]`); the last step may be an attribute (`@Ccy`). Namespaces and
prefixes are ignored. Functions, unions, `//` and `..` are not allowed.

In YAML flow style (`{ ... }`) quote any path that contains `[` or `]`:
`{ path: "/Document/A[2]/@Ccy", present: true }`.

Messages are checked in the codec's canonical form, with the `Document` element as the root.
The Business Application Header is not part of the checked tree yet.

### Missing elements

- In `assert`, a condition other than `present`/`absent` holds when the element is missing ("if present, it must be
  at most 35 characters"). Add a `present: true` rule to require it.
- In `when` (and scenario `where`), a missing element **never** selects, except with `absent: true`. A selector
  "creditor equals X" does not match a message that has no creditor.

## What a rejection reports

Every failed rule becomes a violation naming the rule id, the reason code, the text, the path and expectation, and
the source. The first failed `error` rule, in file order, is the one a rejection reports:

```
Rule EX-001 (reason FF01): A credit transfer message must carry exactly one transaction.
```

## Reason codes

Reason codes in the example pack follow the ISO 20022 external status reason code set (`FF01`, `AM01`, `AM03`,
`RC01`). They were written from memory of that public code list and **have not been checked against it**; check
each code and its meaning against the current ISO external code sets before you rely on it in a real pack.

## Validation

Packs are loaded strictly: unknown keys, duplicate ids and keys, missing sources, bad paths, bad regular
expressions and unsupported formats are errors that name the file and the rule. The JSON Schema is for editors and
CI; a test keeps the schema and the loader in agreement ([ADR 0010](decisions/0010-definition-validation.md)).
Every pack under `rule-packs/src/main/resources/packs/` is loaded and schema-checked in CI.
