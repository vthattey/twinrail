# Inspector UI

Open `http://localhost:8080/ui` (or `/`, which redirects). It shows what the emulator did and **why**:

- **Timeline**, newest first: every message received, every payment and ACH state change, and every clock move,
  each with its virtual time. Expand a row for its details.
- **The rule behind a rejection or return**: the rule id, the reason code, what it means and the specification it
  came from. For an ACH return it shows the return code, its title, the return time frame as the code table's source
  states it, and the 94-character addenda record.
- **Messages** are shown verbatim (the whole XML or ACH file), as text.
- **Accounts** with balance and available funds, and **webhook deliveries** with state and tries.
- **Clock buttons** (+30 s, +1 min, +1 hour, +1 day) move the virtual clock, apply what became due and send due
  webhooks, so you can show a time-out or a return without typing a request.

It updates every two seconds. A row you have expanded stays open while the list refreshes, because rows are history
and never change.

## How it is built (ADR 0006)

Server-rendered HTML from the `ui` module, with [htmx](https://htmx.org) for the refresh and the buttons. No
JavaScript toolchain, no CDN, no network calls: htmx is **vendored** at
`ui/src/main/resources/ui/static/htmx.min.js`.

| | |
| --- | --- |
| Library | htmx 2.0.4 (`https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js`) |
| Size | 50,917 bytes |
| SHA-384 | `HGfztofotfshcF7+8n44JQL2oJmowVChPTg48S+jvZoztPfvwD79OC/LTtG6dMp+` (matches the integrity value htmx publishes for this version) |
| Licence | Zero-Clause BSD, kept next to it as `htmx-LICENSE.txt` |

To update it, replace the file, update this table, and run the tests.

## Security

Message contents (names, ids, whole documents) are untrusted input, so:

- every value goes through one escaping function and nothing builds markup from raw text; CSS classes and element
  ids come from fixed lists or are reduced to safe characters. Tests feed hostile strings through every field.
- pages are served with a strict `Content-Security-Policy` (`default-src 'none'`, scripts and styles only from the
  server itself), `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` and `Cache-Control: no-store`;
  htmx is configured with `allowEval: false`.
- the clock buttons post only a fixed list of steps and require htmx's `HX-Request` header, which a form on
  another web page cannot add, so other pages cannot move the clock.

The emulator is a local development tool and has no authentication; do not expose its port to a network you do not
trust. The UI routes are not part of the API contract and are not in the OpenAPI file.

## Limits

No search or filtering, no per-payment page, no live push (it polls), and webhook deliveries show current state
only, not a history of attempts.
