---
title: "Conventions"
---

Rules that hold across the project rather than in one piece of it. What applies to a single piece is with that piece - [Android](android/README.md), [`ingest`](ingest/architecture.md) and [`www`](www/architecture.md) - and how documentation is written has [a page of its own](writing-documentation.md).

## Colours come from the theme, never from a literal

No component in `www` or the Android app may write a colour value - a hex string, `rgb()`, `Color(0x…)`, a Tailwind palette class such as `bg-emerald-500`, or a stock colour with an opacity applied to approximate a shade. Both applications support a light and a dark theme and the Android one opts into dynamic colour, and a literal follows none of that: it looks deliberate against the theme it was picked on and wrong on the other.

`www` keeps its tokens in `src/routes/layout.css`; the Android app keeps its in [`ui/theme/Color.kt`](../../android/app/src/main/java/com/anonymus09/carsensors/ui/theme/Color.kt), wired up in [`Theme.kt`](../../android/app/src/main/java/com/anonymus09/carsensors/ui/theme/Theme.kt). If a component seems to need a colour the theme does not offer, **say so and ask** - a new colour is added to the theme only after a human has agreed to it, so the palette stays something that was decided rather than something that accumulated one component at a time.

## Commits

**A commit's subject opens with a topic tag**, then a space, a colon and a space: `www : Derive vehicle status in the browser`, `ingest : Throttle the last_seen_at write`. The topic is usually the directory the change lives in, which is usually the piece - `docs`, `tasks`, `ci` and `skills` are common too. It is a convenience for scanning a log that covers four largely independent pieces, not a rule anything enforces.

**`wip` marks a work-in-progress commit**: a feature that is not finished, but has gathered enough work that it should not be lost. It follows the tag - `www : wip live vehicle streaming`.

A commit an AI tool helped to make carries an `Assisted-by:` trailer naming the tool, never `Co-authored-by` - the [AI usage policy](ai-policy.md#disclosure) says why.

## Formatting

**Markdown everywhere is linted by [rumdl](https://rumdl.dev/)**, with the rules and exclusions in [`.rumdl.toml`](../../.rumdl.toml): the vendored agent skills and `.claude/` are left alone, and so is the generated `docs/schema/`.

**Prettier formats `www/` only** - tabs, single quotes, no trailing commas, 100 columns, set in [`prettier.config.js`](../../www/prettier.config.js) - and not its Markdown, which is rumdl's. Of the generated output, the shadcn-svelte components in `src/lib/components/ui/` and the time zone data in `src/lib/map/generated/` are excluded from it. The generated Drizzle schema is not: `pnpm db:sync` formats it with Prettier as its last step, so it already matches.

The Rust is formatted by `cargo fmt` and the Kotlin by ktlint, as [checking each piece](README.md#checking-each-piece) runs them.
