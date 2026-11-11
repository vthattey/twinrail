# Media

`twinrail-demo.gif` is a recording of the [inspector UI](../ui.md) against a fresh server with
`examples/scenarios/time-out.yaml` loaded:

1. a credit transfer with a zero amount is **rejected**, and the expanded row shows why: rule `EX-002`, reason
   `AM01`, and the source of the rule;
2. a valid credit transfer is **accepted** and stays pending (its funds are reserved, so the account's available
   amount drops by 125.50);
3. the **+30 s** button moves the virtual clock and the payment **times out** at the time it was due, with no waiting.

The two payments were sent with `curl` between frames (the commands are in the [quickstart](../quickstart.md));
the rest is the browser. The rule pack is the bundled illustrative one, not a scheme, and the answers are JSON, not
`pacs.002` messages (see [limitations](../limitations.md)).
