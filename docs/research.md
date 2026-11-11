# Research notes — kickoff (2026-11-08)

Answers to the open questions in `docs/brief.md`. Only pages actually read are cited. Re-check before relying on
any of this; scheme documents change.

## 1. FedNow test access for non-participants

- The FedNow Service Operating Procedures (v3.7, October 2026) say the Customer Testing Environment "enables FedNow
  Participants and Service Providers to test their implementation" and that they "gain access to the Customer
  Testing Environment as part of the onboarding process". The document does not mention a public or non-participant
  sandbox.
  Source: <https://www.frbservices.org/wp-content/uploads/101326-fednow-service-operating-procedures.pdf>
- **Conclusion:** no open sandbox found; testing is tied to onboarding. A local emulator is not redundant. Not yet
  verified: whether a Federal Reserve innovation sandbox ever shipped (the 2021 page URL returned 404) or what
  third-party providers offer. Those were seen only in search snippets, so they are not cited.

## 2. RTR message set and status

- Payments Canada's RTR message page lists: `head.001`, `pacs.008`, `pacs.002`, `pacs.028`, `admi.002`, `admi.004`,
  `admi.011`, plus credit-transfer, payment-status, reject and heartbeat flows, "Latest version updated on May 30,
  2025". It lists **no return or recall/cancellation messages**.
  Source: <https://www.payments.ca/rtr-message-portfolio>
- The companion document (v1.4, 30 May 2025) gives the portfolio "envisioned for the RTR at launch" as
  `head.001.001.02`, `pacs.008.001.08`, `pacs.002.001.10`, `pacs.028.001.03`. A second `pacs.002` carries the
  settlement commit advice (v1.2). The text contains no `pacs.004`, `camt.056` or `camt.029`.
  Source: <https://www.payments.ca/sites/default/files/RTR_ISO_20022_message_specification_companion_document.pdf>
- Status: the RTR page says industry solution assurance testing has started and launch is "in the coming weeks" /
  "Q4 2026". It gives no exact Phase 1 date, so **Nov 9, 2026 is unconfirmed**.
  Source: <https://www.payments.ca/systems-services/payment-systems/real-time-rail-payment-system>
- **Consequence:** the brief's v0.1 scope (`pacs.004` return, `camt.056` recall → `camt.029`) is not in the RTR launch
  portfolio. See ADR 0009.

## 3. Terms of use for the RTR specifications

- The message page has no terms, copyright or licence statement.
- The companion document's Legal Notices say: "Confidential Information of Payments Canada for RTR Implementation.
  Not to be disclosed or used for any other purpose without the express consent of Payments Canada." It also says the
  specs are on payments.ca and Swift MyStandards, and JSON schemas are available on request.
- **Conclusion: unresolved risk.** The documents are public, but this notice may restrict reuse in an open-source
  rule pack. Until Payments Canada replies (ISO20022@payments.ca), rule packs must not copy spec text, tables or
  schemas. They may record only rule ids, element paths, reason codes and spec name and version, written in our own
  words. See ADR 0003.

## 4. Prowide ISO 20022 licence

- `prowide/prowide-iso20022` is Apache License 2.0 and models ISO 20022 MX messages (for example `pacs.008.001.02`).
  The page says validation of Swift messages and restricted ISO versions (SEPA, CBPR+, TARGET) are commercial.
  Artefacts are named `pw-iso20022-SRUYYYY-version.jar`, with SRU updates each October. The page did not name the
  current SRU or list releases.
  Source: <https://github.com/prowide/prowide-iso20022>
- **Consequence:** the open edition is usable (Apache-2.0 is compatible with ADR 0002), but CBPR+ and SEPA rule
  packs (v0.3) cannot lean on a commercial validator. Not yet checked: that the RTR versions (`pacs.008.001.08`,
  `pacs.002.001.10`, `pacs.028.001.03`, `head.001.001.02`) exist in the open model, and the JAXB/Jakarta runtime it needs.

## 5. G3 spike, part 1: Prowide open edition (static check, 2026-11-08)

Inspected `com.prowidesoftware:pw-iso20022:SRU2025-10.4.1` from Maven Central (jar and POM downloaded and read; no
JDK was available, so nothing was compiled or run).

- POM licence: Apache License Version 2.0. Depends on `pw-swift-core` SRU2025-10.3.20 (compile) and
  `jaxb-impl` 4.0.6 (runtime).
- The jar contains model classes for every message the plan needs: `MxPacs00800108`, `MxPacs00200110`,
  `MxPacs02800103`, `MxAdmi00200101`, `MxAdmi00400101`, `MxAdmi01100101`, `MxCamt05300108`, `MxCamt05400108`, and
  `BusinessAppHdrV02` (the `head.001.001.02` header). Also present: `MxCamt05200108`, `MxPacs00400110`,
  `MxCamt05600108`, `MxCamt02900109` for later releases.
- **The jar holds no `.xsd` files.** Schema validation against the published RTR/ISO schemas cannot come from this
  jar alone; the codec task must decide how to validate (supply schemas ourselves, subject to the spec-reuse risk in
  section 3, or validate through the model and our own rules).
- The jar is about 57 MB with 32k classes. Image size needs a trimmed/shaded build; measure.
- The artefact `maven-metadata.xml` lists `SRU2025-10.4.0` as latest/release, yet `10.4.1` is downloadable; pin an
  exact version.

## 6. G3 spike, part 2: round-trip and startup (Temurin 21.0.13, Windows 10, 2026-11-08)

Throwaway spike (not committed): parse a synthetic `pacs.008.001.08` with `MxPacs00800108.parse`, read fields, then
marshal with `message()`, re-parse and marshal again.

- **Round-trip works.** Fields read correctly (message id, amount and currency), and parse → marshal → parse →
  marshal is stable (the second output equals the first).
- **Output is not byte-identical to the input.** Prowide rewrites the namespace prefix (`<pacs:Document ...>`
  instead of a default namespace) and normalises timestamps (`2026-11-09T10:00:00Z` became
  `2026-11-09T10:00:00+00:00`). Golden-file tests must compare the generated output, not the input text, and the
  codec should pin its own serialisation settings.
- **Runtime classpath is larger than the POM says.** The jar failed at run time until I added
  `commons-lang3` 3.20.0, `commons-text` 1.15.0 and `gson` 2.14.0 (runtime deps of `pw-swift-core`) and
  `istack-commons-runtime` 4.1.2 and `jakarta.activation-api` 2.1.3 / `angus-activation` 2.0.2 (needed by
  JAXB). Total on disk: about 63 MB of jars. A real build tool will resolve these, so this only matters for
  dependency counting and for image size.
- **Cold start is dominated by JAXB context creation.** Wall time for JVM start + first parse + marshal:
  about 1.4 s (3 runs, 1.38–1.39 s); parse alone about 0.85 s, marshal about 0.13 s. An empty `main` takes about
  0.18 s. With an AppCDS archive built from the same run (a 7 MB `.jsa`), wall time dropped to about 0.95 s
  (0.92–0.99 s, 3 runs). The archive only worked when the application was packaged as a jar.
- **Not measured: container image size.** There is no Docker on this machine. Estimate only: about 63 MB of jars plus a
  JRE; measure once Docker is available (M4).
- Single machine, single message type, tiny payload, no warm-up. Treat these as ballpark figures, not a benchmark.

## 7. NACHA ACH file layout, returns and notifications of change (read 2026-11-10)

M6 needs the ACH file format, return reason codes (R-codes) and change codes (C-codes). **Nacha's own Operating
Rules and Developer Guide were not read** (the rules are paid and the search found no free full copy), so
everything below comes from secondary sources and must be checked against the Nacha documents before the pack
claims anything about the scheme.

Pages actually read:

- **File layout:** eco.com, "NACHA file format explained: records, fields and a worked example"
  (`https://eco.com/support/en/articles/17190470-nacha-file-format-explained-records-fields-and-a-worked-example`,
  a payments vendor's support article, no date). Gave positions, lengths and content for record types 1, 5, 6, 7
  (addenda type `05`), 8 and 9, the computations (entry hash is the sum of the 8-digit receiving DFI ids kept to the
  rightmost 10 digits; totals with implied decimals; block count is lines ÷ 10; the last block padded with lines of
  94 nines), and a worked example. It did not specify the file header reference code or the addenda sequence number
  field, and the example's spacing in the rendered page is uncertain.
- **Return and change codes:** Modern Treasury, "ACH Return Codes"
  (`https://docs.moderntreasury.com/payments/docs/ach-return-codes`, a vendor's API docs, no date). Lists R01–R39
  and R45 with return time frames, and change codes C01–C03, C05–C09, C13 and C14. It does **not** list C04 or
  C10–C12 or the later R-codes, and says nothing about how a return or notification is represented in the file.
- **Return and NOC addenda layouts:** the `addenda99.go` and `addenda98.go` source files of the open-source
  `moov-io/ach` project (Apache-2.0; `https://raw.githubusercontent.com/moov-io/ach/master/addenda99.go` and
  `.../addenda98.go`). Used only to learn the field positions of addenda type `99` (return: return code 4–6,
  original trace 7–21, date of death 22–27, original receiving DFI 28–35, information 36–79, trace 80–94) and type
  `98` (change: change code 4–6, original trace 7–21, original receiving DFI 28–35, corrected data 36–64, trace
  80–94, with the corrected-data layout depending on the change code). No code was copied; Twinrail's codec is its
  own implementation (plan M6).

Pages tried and **not** readable (binary PDF, nothing extracted): a Jack Henry NACHA file formats PDF and an East
West Bank NACHA import specification PDF. A Regions return-code PDF returned 404. Search result snippets were seen
for other sites but those pages were not read and are not cited.

Not verified at all, so not used yet: the routing-number check digit algorithm (R28), which return codes apply to
which SEC codes, settlement and return deadlines beyond the vendor table, and anything about prenotes or IAT.

Consequence for the pack: the NACHA rule pack in v0.1 should cover only what these sources support (record
structure, control totals, a stated subset of return and change codes), mark each rule's `source` with the page it
came from, and list the rest as "not covered".
