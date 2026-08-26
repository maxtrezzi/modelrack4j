# ADR-0013: Watch directories, resolve symlinks, document the macOS caveat

- **Status:** Accepted — symlink strategy and filename filter amended by [ADR-0024](0024-watch-the-symlink-s-directory-not-its-real-path.md)
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

"Watch the config file and reload it" describes the feature but not the mechanism.
`java.nio.file.WatchService` does not watch files — it registers on **directories** — and
several real-world deployment patterns defeat a naive implementation built on the wrong
mental model.

## Forces

- Registration is per directory. Configuration files spread across several directories
  therefore need several registrations, deduplicated, with events filtered by filename.
- Editors and deployment tools commonly write via temp-file-then-rename. The change
  surfaces as `ENTRY_CREATE`, not `ENTRY_MODIFY`. Listening only for modification misses
  ordinary saves.
- The Kubernetes ConfigMap pattern mounts configuration as a **symlink** whose target is
  swapped atomically. Watching the link itself misses the change completely — and this is
  the likely shape of any container deployment, not an exotic case.
- Directories can be removed and recreated, invalidating a registration silently.
- The macOS `WatchService` implementation is polling-based internally. Since push-based
  detection is a stated differentiator (ADR-0002), claiming uniform real-time behaviour
  across platforms would be false advertising.

## Decision

Watch the deduplicated set of **parent directories** of the configured files, filtering
events by filename. Treat `ENTRY_CREATE` and `ENTRY_MODIFY` identically — both feed the
debounce from ADR-0008. Resolve symlinks to their real path at registration and re-resolve
after each event. Re-register when a watched directory is lost.

Measure actual macOS latency and document it, rather than asserting real-time behaviour
everywhere.

A verification spike gates implementation
([Task 0.8](../tasks/phase-0-verification.md#task-08--watch-strategy-spike)): confirm
filename filtering, temp-file-rename behaviour, symlink target swap, and macOS latency on
the target platforms before building the watcher.

## Consequences

- Behaviour is honest and portable, and the ConfigMap case works.
- Cost: materially more implementation care than "watch a file", and platform-specific
  behaviour that must be tested rather than assumed.
- The test suite has to cover temp-file-rename arriving as CREATE, symlink target swap,
  and debounce collapsing rapid successive writes into a single reload. These need real
  temporary directories and asynchronous assertions.
- Documented latency, including the macOS figure, belongs in the README next to the
  real-time claim — not buried, since it qualifies a headline differentiator.
