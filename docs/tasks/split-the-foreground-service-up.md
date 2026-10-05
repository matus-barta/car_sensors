---
title: "Split the foreground service up"
status: backlog
area: android
depends_on: []
---

detekt records three findings in its baseline rather than at the current
threshold, and all three are the same observation: `TelemetryForegroundService`
is a large class, with too many functions, containing one long method. They are
baselined rather than configured away because they are true.

The service does several separable jobs. It owns the armed and recording state
machine; it registers and reads sensors; it listens to power and decides which
tier of work the battery still justifies; it assembles and writes samples; and
it decides when to upload and when to push a position live. The notification,
the heading and the sensor labels have already gone to classes of their own -
`LoggerNotification`, `HeadingTracker`, `sensorAccuracyLabel` - which took it
from 1,437 lines to about 1,190 but left it over both thresholds, at 29
functions against 20. The state machine in particular wants lifting out
into something that takes charge, battery level, whether movement was confirmed
and how long ago as arguments and returns the state that should follow - which
would also make it decidable in a plain JVM test, where today it needs a device.

Nothing is broken, so this is not urgent. It is recorded because the baseline
would otherwise be the only trace of the decision, and a baseline entry read
years later looks like something that was ignored rather than something that
was weighed.

**Related.** Makes [Do not let the logger state outlive the service that reports
it](do-not-let-the-logger-state-outlive-the-service-that-reports-it.md) and the
storage warning in [Keep unuploaded data until the storage runs out, and say so
first](keep-unuploaded-data-until-the-storage-runs-out-and-say-so-first.md)
easier, since both change the service.
