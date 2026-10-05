---
title: "Say on the map that a position is not current"
status: backlog
area: www
depends_on: []
---

The info card now distinguishes when a vehicle was last heard from and when it
was last located, because those come apart: rows without coordinates - events,
and samples whose fix had gone stale - still count as contact, so a device can
report all day while the newest position anyone has stays where it last had one.

The map does not draw that distinction. A marker's colour comes from the status,
which is read from the last contact, so a vehicle that has been parked for weeks
sits at week-old coordinates in confident green. The card explains it if the
vehicle happens to be selected; the map does not, and the map is what somebody
looks at first.

What to do about it needs deciding rather than guessing. Colouring the marker by
the age of the position instead would be wrong the other way, since it would
report a vehicle as offline while it is demonstrably reporting. Two states are
being shown through one channel, so the answer is probably a second channel - a
hollow or dashed marker for a position that is not current, keeping colour for
whether the device is in touch. Worth sketching before building, and worth
checking against what the marker already has to say at three zoom levels.
