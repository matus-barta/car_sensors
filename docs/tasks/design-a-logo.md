---
title: "Design a logo"
status: backlog
area: docs
depends_on: []
---

The project has no logo, and every place one would go shows a default instead:

- the Android app's launcher icon is Android Studio's default robot
  ([`ic_launcher_foreground.xml`](../../android/app/src/main/res/drawable/ic_launcher_foreground.xml),
  and the `mipmap-*` densities beside it);
- the web application's favicon is the one SvelteKit's template ships,
  [`favicon.svg`](../../www/src/lib/assets/favicon.svg);
- the documentation site sets none, so its browser tab asks for a
  `favicon.svg` that does not exist;
- the README opens with the project's name and badges, where projects like
  [Hauk](https://github.com/bilde2910/Hauk) and the
  [OwnTracks Recorder](https://github.com/owntracks/recorder) show theirs.

A logo has to work at a launcher icon's size and as an Android adaptive icon,
whose foreground is cropped to a circle or a rounded square by the launcher,
and read in both the light and the dark theme - its colours chosen with the
themes, as [the colour convention](../development/conventions.md#colours-come-from-the-theme-never-from-a-literal)
asks of everything else.
