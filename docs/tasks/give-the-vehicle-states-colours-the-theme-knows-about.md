---
title: "Give the vehicle states colours the theme knows about"
status: backlog
area: www
depends_on: []
---

Not a rule violation so much as a gap in the palette. MapLibre's paint
properties take colour strings and cannot read a CSS custom property, so hex in
`vehicle-map.svelte` is that library's interface rather than a colour invented
to dodge the theme. What is worth fixing is that the *values* live in two places
and agree only because somebody matched them by hand: the map paints `#10b981`,
`#f59e0b` and `#94a3b8` for the three vehicle states while
`vehicle-status-style.ts` says `bg-emerald-500`, `bg-amber-500` and
`bg-slate-400` for the same three.

`layout.css` has nothing to point either of them at. The palette runs to
`primary`, `muted`, `destructive` and the rest, and none of those means "this
vehicle reported a minute ago". Tokens for `--vehicle-online`,
`--vehicle-stale` and `--vehicle-offline` in both the light and dark blocks
would give the badge and the map one source, but which colours they should be
is a decision to take rather than a matter of promoting whatever is there now.

The awkward part is the map, and it is worse than it first looks. The styling
in question is the two `addLayer` calls in `addVehicleLayers`: the marker
layer's `circle-color`, a `match` on the vehicle's status that picks one of the
three, plus its white `circle-stroke-color`, and the selected-vehicle ring's
fill and stroke above it. Those are values in a style object handed to MapLibre
once, not CSS - nothing re-reads them when a stylesheet changes.

Three separate problems, in increasing order of annoyance.

MapLibre cannot be given `var(--vehicle-online)`; a paint property takes a
colour string, so a token has to be resolved to a value first.

The obvious way to resolve one does not produce something MapLibre accepts.
`getComputedStyle(document.documentElement).getPropertyValue('--vehicle-online')`
hands back the custom property as authored - `oklch(0.7 0.15 160)` - and every
token in `layout.css` is `oklch`. MapLibre's colour parser (6.7, in October 2026) knows `rgb` and
`hsl` and has no idea what `oklch` is, so that string is rejected outright.
Reading it back off an element's computed `color` returns `oklch` too, and
`color-mix(in srgb, …)` only gets as far as `color(srgb …)`, which the parser
does not know either. What does work is rasterising it - fill one pixel of a
canvas with the token and read the bytes back with `getImageData`, which gives
`rgba(35, 186, 125, 1)` and parses fine. That is a real technique rather than a
hack, but it is a conversion step nobody would guess at from the outside.

And it has to happen again on every theme change, since the same token resolves
to a different colour under `.dark`. `mode-watcher` toggles that class on the
document element, so something would have to observe it and call
`map.setPaintProperty` for each affected property, or the map keeps whatever
palette it was built with.

Against that, the duplication costs one comment asking that two files be kept in
step, and there are three states. The tokens are still worth adding so the badge
has something to name; wiring the map to them is the part to leave until there
is a fourth state or somebody actually changes a colour.

**Related.** [Say on the map that a position is not
current](say-on-the-map-that-a-position-is-not-current.md) changes the same
marker.
