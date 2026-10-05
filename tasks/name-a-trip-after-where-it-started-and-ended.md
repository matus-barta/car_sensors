---
title: "Name a trip after where it started and ended"
status: backlog
area: trips
depends_on: []
---

A trip named by its endpoints is far easier to find than one named by a
timestamp. Within a town that means the street it started on and the street it
ended on; between towns, their names; between countries, the countries as well.

The lookups themselves belong to the geocoding service in
[Put reverse geocoding behind a service of its own](put-reverse-geocoding-behind-a-service-of-its-own.md)
rather than here.
What stays with trips is the rule: which component to use at which scale, and
what to fall back on. Store the structured pieces the service returns - road,
town, country for each end - rather than only the rendered name, because the
rule is presentation and will be adjusted, and keeping the components means
adjusting it without asking anybody to resolve the same place twice.

Worth deciding what a trip is called when the rule cannot be applied: a motorway
slip road with no street name, a geocoder that is down, coordinates in the
middle of nowhere. A trip with no name is worse than a trip named after its
coordinates.
