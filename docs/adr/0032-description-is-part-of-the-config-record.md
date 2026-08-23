# ADR-0032: `description` is an ordinary part of the config record

- **Status:** Accepted
- **Date:** 2026-08-23
- **Supersedes:** —
- **Amends:** —

## Context

Configuration names are short by design — `SL`, `SH`, `CR` — because they are typed by the
application on every lookup. They are also opaque to everyone who did not write the file: an
operator looking at a menu, an admin screen, or a log line has three initials and no way to
tell which model is the cheap one.

So each block gains an optional `description`: one short line of prose, read by nobody in the
library. The question this ADR settles is not whether to have it — that is obvious — but
whether it participates in the per-name diff.

[ADR-0006](0006-named-configurations-with-per-name-diffing.md) says a reload rebuilds a
bundle when its parsed `LlmConfig` is no longer equal to the previous one. `LlmConfig` is a
record, so "equal" means every component. Adding a component that has no effect on the built
objects means editing a comment-like string rebuilds a `ChatModel`.

## Forces

- **Rebuilding on a prose edit is waste**, and it is visible waste: `onReload` fires with the
  name in `updated`, so a listener that logs, alerts, or warms a cache reacts to a
  documentation change.
- **Excluding it means hand-writing `equals` and `hashCode` on a record whose entire point is
  that it does not have to.** ADR-0006's rule is currently one sentence — *the diff is record
  equality* — and it stays true only while the record has nothing carved out of it. The next
  contributor adding a field would have to know which half of the record counts.
- **A carve-out is silently wrong in one direction.** If description were excluded, the
  bundle carried over would hold the *old* description in its `config()`, so
  `registry.get(name).config().description()` would keep returning superseded text
  indefinitely. That is a stale read with no signal, which is worse than an unnecessary
  rebuild.
- **The cost of the rebuild is small and local.** Building a bundle constructs LangChain4j
  model objects; it makes no network call. Only the edited block is rebuilt — every other
  name in the snapshot is carried over by the same equality check.

## Decision

`description` is an ordinary record component with no special treatment. Editing it alone
changes the config, rebuilds that bundle, and reports the name in `ReloadChange.updated()`.

It is `Optional<String>`, absent when the file does not supply it. A present-but-blank value
is a validation error rather than a synonym for absent, and the message names
`description = null` as the way a higher layer clears one set by a lower layer — HOCON's null
removes the key, so the merged result has no path at all.

## Consequences

- ADR-0006's rule survives intact: the diff is record equality, with nothing to remember.
- A prose-only edit rebuilds one bundle and notifies listeners. Accepted, and documented in
  the README so it is not a surprise.
- `config().description()` is always the description that is in the file right now, because
  the bundle holding stale text is exactly what the carve-out would have produced.
- If a future field genuinely must not affect the diff — a large blob, or something changing
  on a timer — it does not belong in `LlmConfig` at all. That is the shape of the fix, rather
  than a custom `equals`.
- **Do not "optimise" this by excluding description from equality.** The saving is one
  builder call; the cost is a permanently stale accessor and a diffing rule that no longer
  fits in a sentence.
