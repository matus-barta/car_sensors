---
title: "Fix the map's attribution button in the dark theme"
status: backlog
area: www
depends_on: []
---

The map's attribution - the "i" in the bottom right corner, MapLibre's compact
attribution control - looks wrong in the dark theme: once open, its button is a
pale circle at the end of a dark panel, where in the light theme it blends into
the panel. The dark screenshot in the [README](../../README.md) shows it.

The cause is the dark-theme rule in
[`vehicle-map.svelte`](../../www/src/lib/components/vehicle-map.svelte), which
turns the button's black icon white with `filter: invert(1)`. A filter applies to
the whole element, so it inverts the button's background as well: the
`--popover` colour set on it in the block above comes out light. The navigation
buttons get the same filter, but on `.maplibregl-ctrl-icon`, an element inside
the button that carries only the icon, so their background is left alone.

A fix keeps the icon from the theme without a filter on the button itself -
the icon is a background image, so for example a mask in `currentColor`, or the
filter moved to a pseudo-element that carries only the icon - and is checked in
both themes, with the attribution closed and open.
