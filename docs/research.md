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
