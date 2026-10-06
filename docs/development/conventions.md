---
title: "Conventions"
---

Rules that hold across the project rather than in one piece of it. What applies to a single piece is with that piece: [Android](android/README.md), [`ingest`](ingest/architecture.md) and [`www`](www/architecture.md).

## Colours come from the theme, never from a literal

No component in `www` or the Android app may write a colour value - a hex string, `rgb()`, `Color(0x…)`, a Tailwind palette class such as `bg-emerald-500`, or a stock colour with an opacity applied to approximate a shade. Both applications support a light and a dark theme and the Android one opts into dynamic colour, and a literal follows none of that: it looks deliberate against the theme it was picked on and wrong on the other.

`www` keeps its tokens in `src/routes/layout.css`; the Android app keeps its in [`ui/theme/Color.kt`](../../android/app/src/main/java/com/anonymus09/carsensors/ui/theme/Color.kt), wired up in [`Theme.kt`](../../android/app/src/main/java/com/anonymus09/carsensors/ui/theme/Theme.kt). If a component seems to need a colour the theme does not offer, **say so and ask** - a new colour is added to the theme only after a human has agreed to it, so the palette stays something that was decided rather than something that accumulated one component at a time.
