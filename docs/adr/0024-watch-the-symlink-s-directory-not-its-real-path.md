# ADR-0024: Watch the symlink's own directory; filename filtering cannot gate a ConfigMap swap

- **Status:** Accepted
- **Date:** 2026-08-20
- **Supersedes:** —
- **Amends:** ADR-0013 — the symlink strategy and the filename filter

## Context

[ADR-0013](0013-watch-directories-resolve-symlinks.md) was written from reasoning, and said
so. It decided: watch the parent directories of the configured files, **filter events by
filename**, and **"resolve symlinks to their real path at registration and re-resolve after
each event."** [Task 0.8](../tasks/phase-0-verification.md#task-08--watch-strategy-spike)
existed to test that against a real `WatchService` before the watcher was built.

The spike ran the Kubernetes ConfigMap layout for real — a mount directory holding a
timestamped generation directory, a `..data` symlink pointing at it, and the visible
`app.conf` symlink pointing through `..data` — then performed the atomic swap the way
Kubernetes does: stage a new generation directory, create a temporary `..data_tmp` symlink,
and `ATOMIC_MOVE` it over `..data`.

Two parts of ADR-0013 do not survive that test.

**1. Resolving to the real path and watching *that* directory sees nothing.** The real path
at registration was `…/..2026_08_20_gen1/app.conf`, so the watch was registered on
`..gen1`. The swap does not touch `..gen1` — it creates a *new* directory and re-points the
link. Observed events on that registration: **none at all**. The reload would never fire.

**2. No event in the ConfigMap swap is named after the config file.** Watching the
directory that contains the symlink does see the swap, but the events are:

```
ENTRY_CREATE ..2026_08_20_gen2
ENTRY_CREATE ..data_tmp
ENTRY_DELETE ..data_tmp
ENTRY_CREATE ..data
```

Not one of them is named `app.conf`. ADR-0013's "filter events by filename" would discard
every one and miss the change — the same outcome as not watching at all. The filename filter
and the ConfigMap case are in direct conflict, which reasoning did not surface.

Meanwhile the file's content *had* changed, its real path *had* moved to `..gen2`, and the
visible path still resolved. The information needed to detect the swap was available; only
the trigger was missing.

## Forces

- **The filename filter earns its place in the ordinary case.** Watching a directory for one
  file means every unrelated write in that directory wakes the watcher. In the spike's
  temp-file-then-rename scenario the filter is what discards `app.conf.tmp`'s three events
  and keeps the one `ENTRY_CREATE app.conf` that matters. Dropping it wholesale to fix
  ConfigMap would trade a missed reload for a storm of spurious ones.
- **Symlinked and plain paths need different triggers**, because a symlinked path's change
  arrives under a name the library cannot predict — `..data` is a Kubernetes implementation
  detail, not an API.
- **Re-resolving after each event was right, and is not enough.** ADR-0013 already required
  it; the gap was that no event ever arrived to re-resolve *on*.
- **Falling back to polling for symlinked paths** would work and was considered. It loses the
  push-based property that [ADR-0002](0002-scope-to-langchain4j-llm-configuration.md) names
  as a differentiator, and it is unnecessary — the swap is observable, just not under the
  expected name.

## Decision

**Register on the directory containing the configured path itself — the symlink, when it is
one — and never on the resolved real path's directory.** Resolution is still performed, but
to *read* the file and to *detect* that it changed, never to choose what to watch.

**The filename filter is conditional on the watched path not being a symlink:**

- **Plain file** — filter events by filename, as ADR-0013 said. Unchanged.
- **Symlinked file** — accept **any** event in the watched directory as a trigger, then
  re-resolve the path and compare. A reload proceeds only if the resolved real path or the
  file's content actually changed, so the extra wakeups cost a resolve and a comparison, not
  a rebuild.

The comparison against the previous snapshot is what keeps this honest: spurious wakeups are
absorbed by [ADR-0006](0006-named-configurations-with-per-name-diffing.md)'s per-name diffing
and [ADR-0012](0012-reload-atomicity-is-snapshot-wide.md)'s staged swap, which already
require that an unchanged config produce no visible change.

**Everything else in ADR-0013 stands, and the spike confirmed it:** watch deduplicated parent
directories; treat `ENTRY_CREATE` and `ENTRY_MODIFY` identically; re-register when a watched
directory is lost.

## Consequences

- The ConfigMap case works, and it works push-based — no polling fallback, so ADR-0002's
  claim holds in containers, which is where this library will most often run.
- **The watcher now has two modes**, and the mode is chosen per configured path by asking
  whether it is a symlink. That check must be re-made when a path is re-registered: a
  deployment can replace a plain file with a symlink between one reload and the next.
- **Symlinked paths do more work per event** — every unrelated write in the mount directory
  causes a resolve and a compare. In a ConfigMap mount that directory contains only the
  config and Kubernetes' own bookkeeping entries, so the traffic is small and bounded.
- **Do not "optimise" the symlink branch by filtering its events by filename.** It looks like
  a missing filter and reads like an oversight. It is the entire reason the ConfigMap case
  works, and restoring the filter reverts to a watcher that silently never reloads in
  Kubernetes — the worst failure shape available, because it looks healthy.
- The regression test for this needs the full three-level layout — generation directory,
  `..data` link, visible link — and must assert on a swap performed by atomic rename. A test
  that merely retargets a symlink with delete-then-create does not reproduce the case.
