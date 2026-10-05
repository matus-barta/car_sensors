---
title: "Allow cleartext to a private address, and only to a private address"
status: backlog
area: android
depends_on: []
---

Release builds refuse `http://` outright, which was the right instinct and the
wrong rule. The ordinary way this is used is a phone on the same network as the
server - parked on the drive within reach of the house Wi-Fi, or carried
indoors - uploading to a machine that has no certificate and no name on the public
internet. Demanding HTTPS there asks someone to run a certificate authority for
a server only they can reach.

The relaxation should be an advanced option, and it should relax the rule rather
than remove it: `https://` anywhere, `http://` only to an address that cannot
leave the local network. Public addresses stay refused whatever the option says,
so the setting cannot be turned into "send my credential to anyone".

Judging that by the *destination* is what makes it sound. Asking whether the
device itself holds a public address answers a different question and answers it
wrongly - behind a hotspot, behind carrier-grade NAT, on a guest network, the
device has a private address and a perfectly good route to the internet. A
destination in 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8 or
169.254.0.0/16, or their IPv6 counterparts `::1`, `fc00::/7` and `fe80::/10`, is
unroutable across the public internet as a matter of fact rather than of
configuration.

A hostname should be allowed for the sake of not typing an address, but it has
to be judged on what it resolves to rather than how it is spelled, and resolved
again when the upload is actually made rather than only when the setting is
saved. A name that resolved privately yesterday can resolve publicly today. That
gap cannot be closed completely without connecting by address and carrying the
name in a header, which is more than this is worth - but the check belongs at
the point of use, not only at the point of entry.

The awkward part is that Android's control over cleartext is app-wide.
`usesCleartextTraffic` sits in the manifest, and while a network security
configuration can permit cleartext for named domains, those names are static
resources compiled into the package and cannot express a range, let alone one
the user chooses at runtime. So the platform's own backstop has to come off and
the rule has to live in `ServerUrl` instead. That is a real loss of a guarantee
and worth being deliberate about: what stops a credential going out in clear is
then the app's own arithmetic and nothing beneath it.

Whichever way it goes, the switch should not be `usesCleartextTraffic`. Android
17 announced a plan to deprecate the manifest attribute and points apps at a
network security configuration instead, where `<base-config
cleartextTrafficPermitted="true">` is the same app-wide switch in its supported
form. The debug manifest's `android:usesCleartextTraffic="true"` wants moving
to a debug-only `network_security_config.xml` at the same time, before the
attribute stops being honoured rather than after.

The precondition for this has now been met. It was worth waiting for the
per-device token, because what used to travel over cleartext was the device id,
which was also the identity and could not be changed without abandoning the
vehicle's history. What travels now is a bearer token that can be rotated the
moment it is suspected, so the relaxation costs far less than it would have.

Worth noting while doing it that RFC 6750 section 5.1 requires a bearer token
to be sent over TLS, and this deliberately breaks that for private addresses -
the reasoning is with the token entry, and rotation is what makes it
affordable.
