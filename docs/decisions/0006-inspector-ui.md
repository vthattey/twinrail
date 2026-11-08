# 0006. Inspector UI

Status: accepted (2026-11-08)

## Context
The UI shows a message timeline and the rule behind each rejection. It must not add a Node build or bloat the image.

## Decision
Server-rendered HTML with htmx, served by the same process, in its own module. The htmx script is vendored in the
repo (no CDN: no network at runtime).

## Consequences
- No JS toolchain in CI; small image.
- Less interactivity than an SPA; acceptable for a read-mostly timeline.
- Templates need escaping discipline because message content is untrusted input.
