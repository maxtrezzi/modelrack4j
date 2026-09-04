# ADR-0050: Watch the file layers, whichever builder method supplied them

- **Status:** Accepted — the mechanism amended by ADR-0051
- **Date:** 2026-09-04
- **Supersedes:** —
- **Amends:** ADR-0042

## Context

[ADR-0042](0042-read-configuration-from-sources-not-files.md) made a layer a `ConfigSource`
and kept `configFiles(List<Path>)` as the shorthand for the common case. Its Decision section
says the shorthand "builds the file sources *and* retains the typed paths for `watch(true)`",
and that "`watch(true)` **without file sources** fails at `build()`".

The implementation was stricter than that sentence. `Builder.chooseNotifier` did not look at
the layers at all: it looked at `watchableFiles`, a field only `configFiles(...)` fills, and
`sources(...)` cleared. So a registry whose every layer was a file was refused for having
none, with a message that named the builder method rather than the situation:

> `watch(true) watches configuration files, and this registry has none — its layers were given through sources(...).`

That is not a cosmetic difference, because `sources(...)` is not optional for everyone.
`store()` ([ADR-0044](0044-store-a-layer-back-as-text-validated-before-it-is-stored.md)) requires the target layer
to be a `WritableConfigSource`, and the only builder method that accepts one is `sources(...)`.
An application that wants to write a layer *and* keep hand edits working — a combination the
library exists to serve — could therefore not have both from the builder.

The way through was documented and does work: build the notifier by hand with
`notifier(FileChangeNotifier.of(paths, debounce))`. Its cost is that the same paths are then
written twice, in two shapes, with nothing comparing them. Drop one from the notifier list and
that layer stops being watched — no exception, no log line, and the symptom is an edit that
never reaches the application, which is the failure this library exists to prevent.

## Forces

**The condition tested the wrong thing, and the ADR it came from already said so.** ADR-0042
spoke of layers ("without file sources"); the code spoke of builder methods. Bringing the two
together is a correction, not a new policy — which is why the only genuinely open question was
the mixed registry below.

**Half-watched could be worse than refused.** A registry mixing a file with a database row
would be watched over its file half only. A caller who reads `watch(true)` as "changes reach
me" would then be half right, and an exception that forces the question is arguably safer than
a guarantee that quietly covers part of the configuration. This argument was put to the owner
and lost, for the reason in the next paragraph.

**The alternative is not "no half-watching"; it is the same half-watching, written out by
hand.** Refusing the mixed case does not make the non-file layer watchable. It sends the
caller to `FileChangeNotifier.of(...)`, which watches exactly the same half — with the extra
path list that can silently drift out of step. The exception buys a moment of thought and
charges a standing hazard for it.

**Whether a layer is a file is knowable at `build()`, statically.** The layer list is complete
and validated before the notifier is chosen, and each layer either implements the
package-private `FileBacked` — `FileConfigSource` and `WritableFileConfigSource`, what
`ConfigSource.ofFile` and `ofWritableFile` return — or does not. `ConfigLoader.parse` already
makes exactly this test, to choose between `parseFile` and `parseString`. Nothing about a
layer's kind changes after `build()`, so counting files is a read of the list rather than a
forecast about how the registry will be used.

**Watching nothing in silence stays refused.** ADR-0042's real concern was a `watch(true)`
that quietly does nothing. That case still exists — every layer is a row — and it still fails
at `build()`.

## Decision

**`watch(true)` requires at least one file layer, and ignores the rest.**

`Builder.chooseNotifier` takes the validated layer list, keeps the layers that are
`FileBacked`, and passes their paths to `FileChangeNotifier`. The `watchableFiles` field is
removed, so `configFiles(...)` and `sources(...)` become exactly equivalent — for watching as
for everything else.

With no file layer at all, `build()` still throws `ConfigValidationException`, with a message
that names the layers instead of the method that supplied them:

> `watch(true) watches configuration files, and none of these layers is one: [llm_config#42]. Supply a ChangeNotifier, or call reload() when the configuration changes.`

The paths handed to the notifier are the **configured** ones, not their resolved targets, so
the watcher still registers on the directory holding a symlink rather than the directory
holding its target ([ADR-0024](0024-watch-the-symlink-s-directory-not-its-real-path.md)).

## Consequences

**`store()` and hot reload are available together from the builder**, which was the point.
The manual's hand-built-notifier recipe narrows to the case that still needs it, and the
duplicated path list — with its silent drift — stops being the price of a normal
configuration.

**This is not a breaking change.** It accepts a builder combination that previously threw, and
changes the text of the exception that remains. No behaviour that used to work behaves
differently: for a registry built with `configFiles(...)`, the paths reaching
`FileChangeNotifier` are the same paths in the same order as before.

**A mixed registry is watched over its file half, and that is now a documented guarantee
rather than an accident.** `watch(boolean)`, `sources(List)` and the manual all say which
layers are watched and which are not. The application remains responsible for the rest, with
`reload()` or its own `ChangeNotifier`.

**An application's own file-reading `ConfigSource` is still not watched.** `FileBacked` is
package-private, so a layer this library did not create cannot declare that it has a file, and
`watch(true)` cannot see one. The way through already exists and is unchanged —
`notifier(FileChangeNotifier.of(...))`. Making `FileBacked` public, or adding a path-shaped
method to `ConfigSource`, would put the filesystem back into the interface ADR-0042 removed it
from, and is deliberately not taken here.

**Do not reintroduce a field that remembers which method was called.** That is what
`watchableFiles` was, and it made the builder answer a question about the layers by looking at
its own call history. The layers are the source of truth, and they are complete by the time
the question is asked.
