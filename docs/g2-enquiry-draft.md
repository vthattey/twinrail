# G2: enquiry to Payments Canada (draft, not sent)

Gate G2 in the [v0.1 plan](plan-v0.1.md): may a public, open-source rule pack reference the Real-Time Rail
specifications? It blocks M5 (the RTR pack), and the v0.1 release waits for the answer (decision of 2026-11-10).

The text below is a **draft for the maintainer to review, edit and send** from their own address. Nothing has been
sent. Replace the bracketed parts. The address comes from the companion document's notice and the research notes
(`research.md`, section 3); confirm it is still the right contact before sending.

---

**To:** ISO20022@payments.ca
**Subject:** Question: referencing the RTR ISO 20022 specifications in an open-source test emulator

Hello,

I maintain Twinrail, an open-source (Apache-2.0) tool for developers: a local emulator of payment rails that runs
on a laptop or in CI, with no network access. It is a development aid. It does not certify anything, it does not
replace Payments Canada's testing or any participant's onboarding, and its documentation says so.

I would like to add a rule pack that makes the emulator behave like the Real-Time Rail for the messages in the
published RTR message portfolio (`pacs.008`, `pacs.002`, `pacs.028`, `admi.002`, `head.001`), so that developers can
test their integrations before they reach the RTR test environments.

The companion document (v1.4, 30 May 2025) carries this notice: "Confidential Information of Payments Canada for
RTR Implementation. Not to be disclosed or used for any other purpose without the express consent of Payments
Canada." The same document is published on payments.ca. Because of that notice I want to ask before I publish
anything, rather than assume.

**What the rule pack would contain.** Only my own plain-language descriptions of checks, each with: a rule id, the
path of the ISO 20022 element it concerns (for example `/Document/FIToFICstmrCdtTrf/GrpHdr/NbOfTxs`), the reason
code it produces, and a reference to the specification name and version it was derived from. I would not copy
specification text, tables, diagrams or schemas into the repository.

**What I would like to know:**

1. May a public open-source rule pack reference the RTR specifications by name and version, and describe checks in
   its own words with element paths and reason codes, as above?
2. If not as described, what would be acceptable, or what permission would I need to request, and from whom?
3. May the project describe itself as compatible with the RTR message set (descriptively, without using Payments
   Canada or RTR names or logos in the product name), and would you want any particular wording or disclaimer?
4. The companion document says JSON schemas are available on request. If I obtained them, may they be used in the
   project's own tests without redistributing them?
5. Is there a preferred route for feedback from implementers who find that the published specification and the
   emulator's behaviour differ?

Twinrail is at https://github.com/vthattey/twinrail, and the ["limitations" page](limitations.md) states plainly what it
does and does not do. I am happy to share anything else you need, to remove or change a reference, or to talk it
through.

Thank you,
[Your name]
[Role or organisation, if any; Twinrail is a personal project]
[Contact details]

---

## Before sending

- Check the contact address and the quoted notice against the current documents; they were read on 2026-11-08.
- Decide whether to send as an individual or on behalf of an organisation. Twinrail is meant to stay free of any
  employer or client information (see `CLAUDE.md`), so use a personal address if that is true for you.
- Keep the reply. If it grants permission, record the wording in the RTR rule pack's `source` entries and update
  ADR 0003; if it refuses, update ADR 0009 and the README to say the RTR pack will not be built.
