# ACH (NACHA) files

> Status: M6 done for v0.1, within the limits below. **The record layouts and the return and change code
> tables come from secondary sources** (a vendor article, a vendor's API docs, and an open-source
> implementation's field documentation); Nacha's own rules and developer guide were not read. See
> [research.md](research.md), section 7, for exactly what was read and what was not. Twinrail complements
> scheme and network testing; it does not replace it.

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

## Sending files: the ACH engine

`POST /ach/files` (plain text, 94-character records) receives a file. A file that is not structurally an ACH file
gets a 400 naming the line. A file whose control totals, padding or header values are wrong is refused **whole**
with a 422 and a list of problems with stable ids (`nacha.batch.total-credit`, `nacha.file.padding`, ...); no
public file-level reason code has been read, so none is invented. Otherwise every entry is accepted and, with no
scenario, **settles at once**: a credit moves money from the system account `ACH-SETTLEMENT` to the receiver's
account, a debit the other way. Accounts work as in [lifecycle.md](lifecycle.md): accounts you open cannot be
overdrawn, unknown accounts are created on first use. Entries are in USD (an ACH file carries no currency).

A **debit that the receiver's opened account cannot cover is returned as R01** by the emulator (insufficient
funds) without booking anything. Zero-dollar prenote entries settle without booking.

Read entries with `GET /_twinrail/ach/entries` and `/ach/entries/{id}`; the clock endpoints
(`/clock/advance`, `/clock/set`) apply what has become due and report it in `achTransitions`.

### Returns and notifications of change

Drive them with scenarios (`return`, `noc`; see [scenarios.md](scenarios.md) and
[ADR 0011](decisions/0011-ach-scenarios-and-outputs.md)). A **return** books at its due time on the virtual
clock (`after` the settlement) and reverses the booking whatever the balance, because a return must be booked. The
entry shows the code, its title, the **return time frame as the code table's source states it**, and the 94-character
type 99 addenda record. A **notification of change** attaches to a settled entry, which stays settled, with the
type 98 addenda record and the corrected data laid out for the change code.

The code table is `rule-packs/.../codes/nacha-codes-1.yaml`: return codes R01–R39 and R45, and change codes C01–C03,
C05–C09, C13, C14, each with the source it came from. C04, C10–C12 and later return codes are absent because the
source page does not list them. Scenario files that use a code not in the table are refused when loaded.

## Limits

- **No return or change entry file.** Twinrail reports a return or change as data plus the addenda record, not as a
  complete ACH return file, because the entry-level fields of return and change entries (their transaction codes and
  related fields) are not in any source read. The addenda record's trace number holds the original entry's trace
  number for that reason.
- Transaction codes known: 22, 23, 27, 28, 32, 33, 37, 38. Others are reported by `NachaCheck`.
- Change codes supported in scenarios: C01, C02, C03, C05, C06, C07, C09 (the ones with a documented corrected-data
  layout). `reject` and `drop` do not apply to ACH entries.
- Not covered: the routing-number check digit (R28), which return codes apply to which SEC codes, return deadlines
  enforced as rules (the time frame is shown, not enforced), IAT, prenote rules, effective-date and settlement-date
  handling (entries settle when the scenario says, not on the effective date), and anything in Nacha's Operating
  Rules that the sources above do not show.
- No webhook events for ACH entries yet.
