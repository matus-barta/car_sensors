---
title: "Conventions"
---

Rules that hold across the project rather than in one piece of it - how colours are chosen, and how documentation is written. What applies to a single piece is with that piece: [Android](android/README.md), [`ingest`](ingest/architecture.md) and [`www`](www/architecture.md).

## Colours come from the theme, never from a literal

No component in `www` or the Android app may write a colour value - a hex string, `rgb()`, `Color(0x…)`, a Tailwind palette class such as `bg-emerald-500`, or a stock colour with an opacity applied to approximate a shade. Both applications support a light and a dark theme and the Android one opts into dynamic colour, and a literal follows none of that: it looks deliberate against the theme it was picked on and wrong on the other.

`www` keeps its tokens in `src/routes/layout.css`; the Android app keeps its in [`ui/theme/Color.kt`](../../android/app/src/main/java/com/anonymus09/carsensors/ui/theme/Color.kt), wired up in [`Theme.kt`](../../android/app/src/main/java/com/anonymus09/carsensors/ui/theme/Theme.kt). If a component seems to need a colour the theme does not offer, **say so and ask** - a new colour is added to the theme only after a human has agreed to it, so the palette stays something that was decided rather than something that accumulated one component at a time.

## Documentation: one kind of page at a time

Each page is one of the kinds [Diátaxis](https://diataxis.fr/) describes, chosen by what its reader came for:

| Kind | The reader wants to | Here |
| --- | --- | --- |
| How-to guide | get a task done | [development setup](README.md), [database migrations](database/migrations.md), [deployment](../deployment/README.md), [working on the Android app](android/README.md) |
| Explanation | understand why | the [system](../overview/architecture.md), [`ingest`](ingest/architecture.md) and [`www`](www/architecture.md) architecture, [database schema ownership](database/ownership.md), [Android build and CI](android/build.md), [continuous integration](ci.md), [the `www` Docker image](../deployment/www-docker-image.md) |
| Reference | look something up | the [database schema](../schema/README.md), the [`ingest` API](../api/openapi.json) |
| Tutorial | learn by doing | none yet |

Steps and the reasons for them go on separate pages that link to each other. A how-to guide keeps to what to do and what to check, and says where the reasons are; an explanation says why, and links to the steps. A sentence of reasoning in a how-to is fine, and so is the short list of rules an explanation arrives at. A whole section of the other kind is not - that is the sign a page wants splitting, as [database schema ownership](database/ownership.md) was split from [database migrations](database/migrations.md).

The sections themselves - Overview, Deployment, Development and the pieces under it - are by area, not by kind. That is this project's choice rather than Diátaxis's, which [prescribes a structure by kind](https://diataxis.fr/how-to-use-diataxis/), grown from the inside as pages are improved rather than laid out in advance. Pages of one kind gathering in an area would be the time to reconsider.
