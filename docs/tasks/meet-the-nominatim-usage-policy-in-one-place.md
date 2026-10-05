---
title: "Meet the Nominatim usage policy in one place"
status: backlog
area: geocoding
depends_on:
  - put-reverse-geocoding-behind-a-service-of-its-own
---

Concentrating this in the geocoding service is most of the reason to have one -
every obligation lands once instead of in every consumer.

The public Nominatim instance is very likely the right provider rather than the
one to avoid. Its policy allows one request a second in general, and four a
minute for a script that runs repeatedly or longer than a day, which is the
bucket this falls into. Two lookups per trip and perhaps ten trips a day is
twenty requests against a budget of 5,760 - room for a few hundred vehicles
before the limit comes into view, before any cache hits at all. Nor is this what
the policy objects to: it forbids auto-complete, systematic queries such as
grids and complete listings, scraping and reselling. Asking where the two ends
of a journey happen to be is the sparse, occasional lookup the service exists to
answer.

The obligations, which are obligations and not suggestions. Caching, which the
policy requires outright, since repeating a query is grounds for being blocked.
Attribution wherever results are shown, under ODbL. A provider that can be
changed on request *without a software update*, so it belongs in configuration
rather than in the source. And a User-Agent identifying the application, because
an HTTP library's default explicitly will not do.

That last one wants care, because this is software other people can run.
Nominatim can block on address or on User-Agent; an address is one operator's
problem, but a User-Agent shared by every deployment is everybody's - one
careless instance would take the rest down and nobody could tell themselves
apart. So the header should name the software and its version, and carry a
contact belonging to whoever runs that copy:

```text
car-sensors-geocoder/0.1.0 (+https://example.org/contact)
```

The mechanism matters more than the format: the contact should be required
configuration that the service refuses to start without, rather than a default
that quietly works, because a default that works is a default nobody replaces.
The convention for redistributable software talking to Nominatim is exactly
this - force the operator to set their own, and point them at the policy - and the
reward is that a contactable operator receives an email where an anonymous one
receives a block. It only helps if the address is theirs. The policy is explicit
that a User-Agent is required and silent on whether it should distinguish
deployments; that reasoning follows from how blocking works rather than from a
written rule.

Self-hosting stays the answer if the fleet grows by orders of magnitude or
depending on a free service becomes uncomfortable. Photon is the lighter thing
to host - Nominatim wants a region extract and a great deal of memory - and
Komoot's public Photon instance is a middle option, though it publishes no
numbers, only a request to be fair. Keeping the provider configurable makes that
a later decision rather than a rewrite.

On the cache itself: there is no established project worth depending on. The one
purpose-built thing that exists has no users to speak of, and the generic
answer - nginx `proxy_cache` with `limit_req`, or Varnish - only caches identical URLs,
which reverse geocoding rarely produces. Since the results are being stored
anyway, for the structured components trips keep, the cache is that table rather
than a component in front of it. Matching on proximity rather than exact
coordinates would hit far more often, since a car never parks in quite the same
spot twice, but at this volume that is an optimisation rather than a
requirement.
