# Demand validation (gate G1)

Gate G1 in the [v0.1 plan](plan-v0.1.md): before more is built, check with 5–10 developers that the problem is real
and that Twinrail would be used. This page holds the method and the notes. **No names, employers or quotes that
identify anyone**: record the role and the rail they work on, nothing else.

Status: **not started.** No conversations have been held and there are no findings yet.

## Who to ask

Developers who build or test payment integrations: participants in a real-time rail, payment service providers,
fintechs that originate ACH. Aim for 5–10 people across at least two of those groups. Ask people who have nothing to
gain from being polite.

## What to show

The [README](../README.md) and the demo recording ([docs/media](media/README.md)), which show a rejection with the
rule behind it and a time-out reached by moving the virtual clock. Say plainly what does not exist yet (the
Real-Time Rail pack; see [limitations](limitations.md)).

## Questions

Ask about their past, not about whether they like the idea.

1. How do you test a payment integration today, before the scheme or network test environment?
2. The last time that testing went wrong or took too long, what happened?
3. How long does it take to get access to the test environment, and what do you do while you wait?
4. How do you test time-outs, returns and cut-offs now?
5. What would you need to see before you tried a local emulator? What would stop you?
6. If a rail you care about were missing, would you write a rule pack, or wait?
7. Would you put it in CI? What would that require (image size, start-up time, licence)?

## Notes (one block per conversation)

```
Role / rail:
How they test today:
Pain, in their words (no identifying detail):
Would they use it as it is? (yes / with changes / no)
What they asked for:
```

## Decision rule

Written before any conversation, so the answers cannot move it. Continue as planned if at least **3** of the people
asked describe a recurring pain that Twinrail addresses and say they would try it. If fewer, write up what was
learned here and change direction (for example, ACH and generic ISO 20022 first, or a different audience) before
building more. The plan's original wording, "stop before M4", is moot because M0–M4 are already built; the question
now is whether to go on to the Real-Time Rail pack and the release.

## Findings

None yet.
