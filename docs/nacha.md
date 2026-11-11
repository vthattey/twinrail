# ACH (NACHA) files

> Status: M6 in progress. The file codec is done. Returns and notifications of change driven by scenarios, and the
> rule pack, are not. **The record layouts come from secondary sources** (a vendor article and an open-source
> implementation's field documentation); Nacha's own rules and developer guide were not read. See
> [research.md](research.md), section 7, for exactly what was read and what was not. Twinrail complements scheme and
> network testing; it does not replace it.

## What the codec does

`NachaCodec.parse(text)` reads a file of 94-character records (types 1, 5, 6, 7, 8, 9) into a `NachaFile`, checking
**structure only**: line length, record order, known record types, known addenda types (`05` payment information,
`98` notification of change, `99` return). Errors name the line and what to change. Padding lines of 94 nines after
the file control record are accepted and counted.

`NachaCheck.verify(file)` checks what the file says about itself and returns every problem with a stable id and the
line: batch and file control counts, entry hash, debit and credit totals, block count, padding to a multiple of 10
lines, the fixed header values, control-record fields that must match the batch header, and the addenda indicator.
Parsing and checking are separate so a file with wrong totals can still be read and reported on.

`NachaBuilder` builds a file from field values (amounts in cents) and fills in everything derived: the addenda
indicator, batch and file control records, block count and the fixed header values. `NachaCodec.write` is
deterministic: LF line endings, a trailing newline, last block padded with lines of nines. Parse then write gives
back the same bytes.

Record layouts are data (`Layouts`), not code per record type.

## Limits in this slice

- Transaction codes known: 22, 23, 27, 28, 32, 33, 37, 38 (the ones the sources list). Others are reported by
  `NachaCheck` and refused by the builder.
- Not covered: the routing-number check digit, which return codes apply to which SEC codes, IAT, prenote rules, and
  anything in Nacha's Operating Rules that the sources above do not show.
