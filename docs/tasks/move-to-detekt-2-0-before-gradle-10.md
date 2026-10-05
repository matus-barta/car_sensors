---
title: "Move to detekt 2.0 before Gradle 10"
status: blocked
area: android
depends_on: []
---

The detekt plugin, 1.23.8, calls `ReportingExtension.file(String)`, which Gradle
9 deprecates and Gradle 10 removes. It is the reason every build ends with
"Deprecated Gradle features were used in this build, making it incompatible
with Gradle 10" - `--warning-mode all` names it. 1.23.8 is the last 1.x release,
so no update will fix it. The fix is in detekt 2.0, which was still at
`2.0.0-alpha.6` in October 2026 ([Gradle plugin portal](https://plugins.gradle.org/plugin/dev.detekt)).

detekt 2.0 is not a drop-in update. The plugin id changes from
`io.gitlab.arturbosch.detekt` to `dev.detekt`, and a major version renames
rules and config keys, so `config/detekt/detekt.yml` will need going through,
not only the version bump. Move once 2.0 has a stable release, and before taking Gradle 10 -
until then nothing breaks, it only warns.

The other deprecation in that message, `Configuration.setVisible`, comes from AGP
itself, is not removed until Gradle 11, and goes away with an AGP update rather
than anything done here.
