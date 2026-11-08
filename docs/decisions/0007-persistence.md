# 0007. Persistence

Status: accepted (2026-11-08)

## Context
Runs must be deterministic and fast; replay and golden files matter for CI.

## Decision
State is in memory. A recorded session (seed, clock events, every message, scenario set) can be exported to and
imported from a single file through the control API. No database.

## Consequences
- Restart loses state unless a session was exported.
- The session file format is a compatibility surface and carries a format version.
- Same inputs, seed and clock replay to identical messages and ids.
