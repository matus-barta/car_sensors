---
title: "Give the map a dark style, and let it stay light"
status: backlog
area: www
depends_on: []
---

The web application has a dark theme, but its map stays light: the default
style, VersaTiles' "colorful", has no dark variant, so in the dark theme only
the interface around the map turns dark.

**First, find a dark style worth using.** Most dark maps look poor - Google's
included - so this starts as a search rather than a switch: candidates are
compared on screen, on this application's own map with vehicles on it, before
anything is chosen. A candidate has to read the same tiles the map uses now,
which are Shortbread
([`osm-map-style.ts`](../../www/src/lib/map/osm-map-style.ts)), or the change
moves the tile source as well. VersaTiles serves five dark styles for those
tiles, each with the `versatiles-shortbread` source that `osm-map-style.ts`
expects, so each is a matter of setting `PUBLIC_OSM_STYLE_URL` to try - the
first candidates:

- `colorful-dark`, the dark form of today's style
  ([style](https://tiles.versatiles.org/assets/styles/colorful-dark/style.json),
  [preview](https://versatiles.org/versatiles-style/colorful-dark.png));
- `natural-dark`
  ([style](https://tiles.versatiles.org/assets/styles/natural-dark/style.json),
  [preview](https://versatiles.org/versatiles-style/natural-dark.png));
- `muted-dark`
  ([style](https://tiles.versatiles.org/assets/styles/muted-dark/style.json),
  [preview](https://versatiles.org/versatiles-style/muted-dark.png));
- `gray-dark`
  ([style](https://tiles.versatiles.org/assets/styles/gray-dark/style.json),
  [preview](https://versatiles.org/versatiles-style/gray-dark.png));
- `toner-dark`
  ([style](https://tiles.versatiles.org/assets/styles/toner-dark/style.json),
  [preview](https://versatiles.org/versatiles-style/toner-dark.png)).

All of them, light and dark, are side by side on the
[VersaTiles style gallery](https://versatiles.org/versatiles-style/), and
[`@versatiles/style`](https://github.com/versatiles-org/versatiles-style) can
generate a variant with its own colours if none is right as it is. Styles built
for another schema - OpenMapTiles, or Protomaps' basemaps - mean changing the
tile source as well, which also changes what
[the privacy section](../../README.md#privacy) says about who serves the map.

**Then, let the map stay light.** A dark interface with a light map is a
reasonable preference in its own right, and the only good one until a dark
style is chosen. So the map's theme becomes a setting - follow the interface,
or always light - kept per browser, beside the theme switch it qualifies.

A deployment that sets `PUBLIC_OSM_STYLE_URL` to its own style needs a dark
counterpart to set too, or the setting falls back to the light map.
