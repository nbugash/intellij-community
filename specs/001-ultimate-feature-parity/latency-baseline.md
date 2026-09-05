# Latency baseline

Tasks T038 and T116. The figures come from `LatencyBaselineTest`, which asserts nothing about time
and prints. Reproduce with:

```
./tests.cmd --module intellij.platform.remoteDev.protocol.tests \
  --test com.intellij.remoteDev.protocol.LatencyBaselineTest
```

Run on 2026-09-05. Linux, Java 25.0.4.1. 1000 samples per row.

## What the numbers are

A sample is the real protocol path: serialize the message, write the frame, read it back,
deserialize. That is measured on the clock. The link is modelled, not real: its round trip is added
to the sample, because a test that sleeps 100 ms a thousand times measures the scheduler.

So each figure is protocol cost plus modelled link cost. It is a floor. It contains no editor, no
host, and no rendering, and no real session will beat it.

## The run

| Message | Echo | Link | p50 | p95 | max |
| --- | --- | --- | --- | --- | --- |
| keystroke | round trip | 0 | 12.2 us | 30.3 us | 9.13 ms |
| completion | round trip | 0 | 30.2 us | 117.6 us | 248.8 us |
| keystroke | local echo | 0 | 1.2 us | 2.7 us | 15.9 us |
| completion | local echo | 0 | 28.5 us | 35.8 us | 122.2 us |
| keystroke | round trip | 100 ms | 100.0011 ms | 100.0024 ms | 100.0163 ms |
| completion | round trip | 100 ms | 100.0276 ms | 100.0373 ms | 100.0818 ms |
| keystroke | local echo | 100 ms | 957 ns | 2.2 us | 27.7 us |
| completion | local echo | 100 ms | 23.8 us | 32.2 us | 295.3 us |

The 9.13 ms maximum in the first row is JIT warm-up. There is no warm-up pass on purpose: it is a
real cost the first keystroke of a session pays, and hiding it would make the table prettier and
less true. It does not reach the 95th percentile.

## Against SC-002, task T116

SC-002: over a 100 ms round-trip connection, 95% of keystrokes appear in under 50 ms and 95% of
completion popups in under 300 ms.

| Half of SC-002 | Budget | Round trip | Local echo |
| --- | --- | --- | --- |
| Keystroke | 50 ms | **100.0024 ms, fails** | 2.2 us, passes |
| Completion | 300 ms | 100.0373 ms, **passes** | 32.2 us, passes |

The two halves do not have the same answer, and that is the finding.

**The keystroke half cannot be met by a round trip, at any protocol speed.** Half the link costs
more than the whole budget before the host has read a byte. This is arithmetic, not a measurement:
no optimisation reaches it. The keystroke budget is a statement about the architecture. It is met
only if the client draws the character before the host has seen it, and reconciles afterwards.

**The completion half is met by a round trip already**, with 200 ms to spare. It needs no local echo,
no speculation, and no cache. A design that added them for completions would be adding complexity to
buy nothing.

**The protocol is not what is at stake either way.** At a 100 ms link the protocol contributes 2.4 us
of the 100.0024 ms a keystroke costs: 0.002%. Making serialization or framing faster cannot move
either verdict. The budget is spent entirely on the link and on the decision to wait for it.

## Status of SC-002

**Not met, and not met for a structural reason.** This fork has no local echo. `PendingEditLog`
records edits made while a client is disconnected, which is FR-015's concern and a different one:
it replays after an outage, it does not draw before a confirmation.

There is no regression to record here, because there is no earlier measurement to regress from. This
is the first baseline.

## What closing it needs

Not a faster protocol. A client that renders an edit locally on the keystroke, sends it, and
reconciles when the host answers, including when the host answers differently.

The hard part is not the drawing; it is disagreement. Two questions have to be settled before any
code: what the client shows between drawing an edit and having it confirmed, and what it does when
the host's document has moved underneath it. Those belong in the contract, next to the framing and
version rules, because a client and a host must agree on them to interoperate at all.

Until that exists, the honest statement is the one above: the completion half of SC-002 holds today,
and the keystroke half does not.
