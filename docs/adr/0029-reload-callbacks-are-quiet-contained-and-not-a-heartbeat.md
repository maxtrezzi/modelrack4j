# ADR-0029: A reload callback means something changed — nothing else

- **Status:** Accepted
- **Date:** 2026-08-23
- **Supersedes:** —
- **Amends:** —

## Context

[ADR-0012](0012-reload-atomicity-is-snapshot-wide.md) settled the shape of the reload
callback: one per successful swap, carrying `updated`, `added` and `removed`. Building it
surfaced three questions it did not answer, each of which a listener can observe and
therefore depend on.

**Not every wakeup is a change.** By [ADR-0024](0024-watch-the-symlink-s-directory-not-its-real-path.md)
a symlinked config path accepts *every* event in its directory, and even a plain file wakes
the watcher when it is saved with identical content. Both produce a parse whose result equals
the live snapshot.

**A listener can throw.** It runs on the watcher thread, which is the thread every future
reload depends on.

**A recovered directory announced nothing.** [ADR-0013](0013-watch-directories-resolve-symlinks.md)
requires re-registering when a watched directory is lost. Whatever was written while nothing
was registered generated events that no longer exist.

## Forces

- **A no-op callback has one real use** — proving the watcher is alive — and a serious cost:
  it makes `onReload` fire on unrelated writes in a ConfigMap mount, so a listener that logs
  or invalidates a cache does so for events that changed nothing. Liveness is a monitoring
  concern with better tools, and `ReloadChange.isEmpty()` would become a check every listener
  must remember to write.
- **Swapping an unchanged snapshot is not free either.** Every name would be handed a new
  bundle instance, defeating [ADR-0006](0006-named-configurations-with-per-name-diffing.md)'s
  carry-over for no observable gain.
- **Against containing listener exceptions:** it hides a bug in application code. But letting
  one out kills the watcher thread, so a bad listener stops all future reloads — silently, in
  the shape this library exists to prevent. The bug is worth reporting; it is not worth the
  feature.
- **Against reloading on recovery:** it fires without a detectable edit, which looks like a
  spurious reload. But a directory that vanished and returned is a redeployment, and the
  alternative is a registry serving pre-deployment configuration until somebody happens to
  touch the file again.

## Decision

**A callback means the configuration actually changed.** A reload whose result equals the
live snapshot swaps nothing and delivers nothing, to either listener. `ReloadChange` is
therefore never empty when observed, and `onReload` is not a heartbeat.

**A listener that throws is contained.** The exception is logged at `error`
([ADR-0028](0028-core-logs-through-slf4j-api.md)), the remaining listeners still run, the
swap that already happened stands, and later reloads are unaffected. A listener may also
close the registry it was called from; that returns immediately rather than waiting for the
watcher thread to join itself.

**Recovering a lost directory counts as a change.** Successful re-registration schedules a
reload as if an event had arrived, and the diff then decides whether anything is published —
so the recovery costs a parse, and is invisible unless the content really moved.

## Consequences

- Listeners can be written for the case they care about, without defending against empty
  changes or repeated identical ones.
- An application that wants a liveness signal for the watcher must get it elsewhere. This is
  a deliberate omission, not an oversight.
- **Do not "fix" the silent no-op reload** by firing an empty change. It looks like a missing
  notification and reads like an oversight; restoring it turns every unrelated write in a
  Kubernetes mount into an application-visible event, which is precisely the traffic
  ADR-0024 accepted on the understanding that the diff would absorb it.
- The regression tests pin all three: an identical rewrite and an unrelated file both produce
  no callback of either kind; a throwing listener neither breaks its reload nor stops the
  next; and a directory deleted, recreated and *then* edited again is still picked up, which
  is only true if the re-registration happened.
