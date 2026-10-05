---
title: "Do not let the logger state outlive the service that reports it"
status: backlog
area: android
depends_on: []
---

`LoggerState` lives in a companion object, so it is process-wide rather than
tied to the service instance. Almost always that is right - a restart under
`START_STICKY` sets it again, and a process death resets it to `OFF` - but a
service killed while its process survives would leave the screen reporting
`RECORDING` for something that stopped.

Reading it back from whether the service is really running would mean binding to
it, which is more machinery than the fault deserves. A cheaper answer is to
treat it as a claim rather than a fact: have the service refresh a heartbeat
while it records, and let the screen say so once the claim has gone stale. Worth
doing only if this is ever seen in practice - it is written down so that a
screen insisting on `RECORDING` while nothing is recorded is recognised rather
than puzzled over.

**Related.** Easier after [Split the foreground service
up](split-the-foreground-service-up.md), once the state machine is a class of
its own.
