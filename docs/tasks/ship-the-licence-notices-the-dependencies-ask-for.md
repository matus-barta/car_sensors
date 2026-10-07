---
title: "Ship the licence notices the dependencies ask for"
status: backlog
area: distribution
depends_on: []
---

The README has no acknowledgements section, and nothing requires one today:

- **OpenStreetMap's data** has to be credited where a map shows it
  ([attribution guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)).
  The web application's map does, in its attribution control, which
  [`osm-map-style.ts`](../../www/src/lib/map/osm-map-style.ts) fills in, and the
  map screenshot in the [README](../../README.md) includes that control, which
  is enough for a static image under the same guidelines. A new screenshot of
  the map keeps the credit in the picture, or carries it beside the picture.
- **VersaTiles' style** is MIT-licensed code that the browser fetches from
  VersaTiles at run time; nothing of it is in the repository or the images
  ([licence](https://github.com/versatiles-org/versatiles-style#licenses)).

What may be missing is in the builds rather than the README. Most dependencies
are under MIT or Apache 2.0, and both ask for their notices to travel with a
copy of the software: MIT's "shall be included in all copies or substantial
portions" ([text](https://opensource.org/license/mit)), and Apache's copy of the
licence and `NOTICE` file
([section 4](https://www.apache.org/licenses/LICENSE-2.0#redistribution)).
Where the builds stand:

- the `ingest` image is the one Rust binary on a distroless
  base ([`Dockerfile`](../../ingest/Dockerfile)), with no notices for the
  crates compiled into it;
- the `www` image copies the production `node_modules`, licence files
  included, but the code bundled into `build/` has not been checked
  ([`Dockerfile`](../../www/Dockerfile));
- the Android app has no screen or file listing its libraries.

Both images are published on every merge, so this is not only a question for
the first release; the Android app becomes one with
[signed builds](publish-signed-builds-to-github-releases-for-obtainium.md).
Tools exist for each - [cargo-about](https://github.com/EmbarkStudios/cargo-about)
for the Rust crates, [AboutLibraries](https://github.com/mikepenz/AboutLibraries)
for an Android licences screen - and adding one is the developer's decision, as
for any tool. Whether the README then also thanks the projects it is built on
is a matter of taste, not licence; if it does, a short list linking each is
enough.

**Related.** [Add features and upgrading sections to the README](add-features-and-upgrading-sections-to-the-readme.md)
is the other README section still to come.
