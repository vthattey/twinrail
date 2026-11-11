# 0011. ACH scenarios and what returns and changes produce

Status: accepted (2026-11-10)

## Context
M6 needs returns and notifications of change (NOCs) driven by scenarios (ADR 0004). An ACH entry is not an XML
message, so the shared matcher cannot read it directly. The sources read so far (`docs/research.md`, section 7)
give the layout of the return addenda (type 99) and NOC addenda (type 98) and the code tables, but **not** the
transaction codes or other fields of the return and NOC entry records themselves, and not the file-level reason
codes.

## Decision
- **Matching.** Each entry is shown to the matcher as a small synthetic XML document of type `nacha.entry`, with
  the elements `TransactionCode`, `RoutingNumber` (receiving DFI id plus check digit), `AccountNumber`, `Amount`
  (decimal, such as `1500.00`), `IndividualId`, `IndividualName`, `TraceNumber`, `SecCode`, `CompanyName`,
  `CompanyId`, `EntryDescription` and `EffectiveEntryDate`. Scenarios use the existing `match.where` paths, for
  example `/Entry/IndividualName`. This reuses the matcher and keeps one scenario syntax.
- **Actions.** Two new actions: `return` (`code` R-code, optional `after` duration from settlement, optional
  `text`) and `noc` (`code` C-code plus the corrected values that code needs). `delay` delays settlement.
  `reject` and `drop` are ISO 20022 behaviours and do not apply to ACH entries: a matching `reject` or `drop`
  leaves the entry to settle normally. Codes must exist in the code table
  (`rule-packs/.../codes/nacha-codes-1.yaml`), else the scenario file is refused when loaded into the emulator.
- **Outputs.** A return or NOC is reported as structured data (code, title, time frame, original trace number,
  times) together with the 94-character **addenda record** (type 99 or 98) built from the sourced layout. Twinrail
  does **not** yet generate a full return or NOC entry file, because the entry-level fields are not in a source
  read. This is stated in the docs.
- **Ledger.** An ACH entry books between the receiver's account and a system settlement account; a return
  reverses the booking. A debit that the receiver's account cannot cover is returned as R01 by the emulator.
- **File-level problems** (control totals, padding, header values) reject the whole file with the stable ids from
  `NachaCheck`. No public file-level reason code has been read, so none is invented.

## Consequences
- ACH scenarios are written like ISO 20022 ones; only the paths and actions differ.
- Scenario files mixing rails are legal; an action that does not fit a rail is ignored for it.
- Return and NOC files can be added later without changing scenarios, once a source covers the entry fields.
