# Payment lifecycle and ledger

A `pacs.008` credit transfer moves through four states. Only `ACCEPTED` can still change.

```
            rules fail / scenario reject / no funds
 received ─────────────────────────────────────────▶ REJECTED
     │
     └─ ok ─▶ ACCEPTED ── settle time reached ──▶ SETTLED
                  │
                  └── time-out reached first ────▶ TIMED_OUT
```

## What happens on submit

1. The **rule pack** is checked. The first failed error-severity rule rejects the payment and the rejection names
   the rule id, reason code and source specification. Warnings never reject.
2. Built-in backstops: an amount that is missing, not a number or not above zero (`AM01`), and a debtor or
   creditor that cannot be identified (`FF01`). These run only if no rule already rejected.
3. The first matching **scenario** decides the behaviour: `reject` (rejection `scenario:<id>`), `delay` (settle
   after that much virtual time) or `drop` (never settle, so the time-out fires). With no match the payment
   settles at once.
4. The debtor's funds are **reserved**: insufficient available funds is `AM04`, a different currency is `AM03`.

## Time moves only when you move it

The engine never moves the clock; after the clock moves, `tick()` applies everything that became due. Each
settlement or time-out is booked at the time it was **due**, not when the tick ran, so jumping the clock by an
hour gives the same ledger as stepping it every second. If a delay is as long as or longer than the time-out, the
time-out wins. The time-out is a test setting (a `Duration` given to the engine), not a scheme value.

Returns and recalls are not part of the v0.1 lifecycle (ADR 0009); NACHA returns arrive with M6.

## Accounts

The debtor and creditor are identified by the first of: account IBAN, account other id, creditor/debtor agent
clearing-system member id.

- `Ledger.open(id, currency, balance)` creates an account that **cannot be overdrawn**.
- An account that a payment mentions but nobody opened is created on first use with a zero balance and **may go
  negative**, so a simple test needs no set-up.
- Funds for accepted payments are reserved, so two pending payments cannot spend the same money. A time-out
  releases the reservation.
- Money only moves between two accounts, so the sum of all balances never changes.

## Statements and notifications

`Statements.camt053(account)` writes a `camt.053.001.08` with opening and closing balances and every booking since
the account was opened. `Statements.camt054(entry)` writes a `camt.054.001.08` for one booking. Both are
deterministic: same seed, clock and activity give the same bytes.

Limits: balance dates use the time the statement was produced, and `BkTxCd` is a generic payments code. Schema
validation is not available yet (see `docs/research.md`, section 5).

> Status: implemented in `core`. The REST endpoints and control API arrive in M4.
