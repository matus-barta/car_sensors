---
title: "Move the workflows to Ubuntu 26.04"
status: backlog
area: ci
depends_on: []
---

Every job is pinned to `ubuntu-24.04`. GitHub moves `ubuntu-latest` to 26.04
from 2026-10-19 (actions/runner-images#14748), and a new image arriving
unannounced is a failure that looks like a code change. Pinned, it arrives when
somebody decides it should.

The emulator jobs are the ones most likely to notice. They rely on what the
image ships - the SDK at `$ANDROID_HOME`, `sdkmanager` under `cmdline-tools`,
readable KVM after the udev rule - and installed the emulator themselves only
once it turned out the image did not. The Rust and web jobs install their own
toolchains, so they should care less.

To move: change `runs-on` to `ubuntu-26.04` on a branch, start "Android -
migration tests" there by hand from the Actions tab so the post-merge devices
run too, open a pull request so the rest run, and fix what breaks. It has to
happen before GitHub retires the 24.04 image, which it announces in that same
repository some months ahead.
