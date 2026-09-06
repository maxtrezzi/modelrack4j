# ADR-0056: Reject a key the schema does not know

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

A key inside a named block that this library's schema does not know is ignored in silence.
`LlmConfig.fromBlock` reads only the paths it knows and nothing enumerates the rest, so a block
carrying `temperatur = 0.9`, `timeuot = 30s` and `memory.max-mesages = 99` builds a registry
that works, with the provider's own default temperature and the library's own 60-second
timeout. Nothing throws and nothing is logged, because no code is in a position to notice.

That is the ordinary cost of a misspelling in a configuration file, and it is higher here than
in a program that reads its configuration once. This library reloads: a value that was correct
yesterday can be replaced by a misspelling today, the reload succeeds, and the application goes
on running with a setting nobody asked for.

Until now the question could not be answered, because nothing distinguished a misspelling from
a value an application had deliberately put inside a block — both are keys the library does not
know. ADR-0055 makes that distinction possible by giving application values a declared place.

## Forces

**A warning is the usual answer and it is the wrong one here**, for a reason that comes from
reloading rather than from taste. With a warning the configuration loads, and then the same
warning repeats on every reload for as long as nobody fixes the file. With an error that state
cannot exist: an unknown key never reaches a live snapshot, so there is nothing to repeat.

**An error fits what a rejected reload already does.** Nothing swaps, the previous snapshot
stays live, one `onReloadFailure` fires. A misspelling therefore cannot quietly degrade a
running application, which is the failure being prevented.

**Strictness is cheap to adopt now and expensive later.** Going from strict to lenient breaks
no existing file; going from lenient to strict breaks every file that had a stray key. At `0.x`
with one consumer this is the cheapest moment there will be.

**Against it stands forward compatibility.** A file written for a later version that adds a key,
read by an earlier one, fails rather than ignoring what it does not understand. That file would
not have behaved as its author intended either way, but the failure is abrupt.

**And an implementation risk pulls harder than it looks.** A declared list of known keys beside
`fromBlock` is a second copy of the same truth, and nothing would catch it drifting: adding a
key to the parse and forgetting the list produces exactly the silent behaviour this decision
removes. `modelrack4j-reference.conf` cannot serve as that list — it holds the defaults and
deliberately omits both the required keys and the ones whose absence is meaningful.

## Decision

A key inside a named block that the schema does not know makes the configuration invalid.

1. **The known keys are produced by the parse, not declared beside it.** `fromBlock` reads
   through a wrapper that records every path it asks for; the unknown keys are the leaf paths
   the parse never touched. Adding a key to `fromBlock` makes it known automatically, so the
   two cannot drift.
2. **Everything under `custom-properties` is known**, by prefix. That sub-block is the declared
   place for what the library does not interpret (ADR-0055).
3. **One error lists every offending key**, not the first. Each carries its origin from
   `ConfigValue.origin().description()`, which names the layer and the line for a file layer and
   the source id and line for a text layer, since `ConfigLoader.parse` already sets the origin
   description from `ConfigSource.id()`.
4. Enumeration uses `entrySet()`, which yields leaf paths — so a misspelling nested inside
   `memory` is reported as `memory.max-mesages` — and which excludes a key cleared with `= null`
   in a higher layer, so the clearing idiom ADR-0032 relies on does not report as unknown.
5. It is a `ConfigValidationException`: the library read something and objected.

## Consequences

**A misspelling is caught at the moment it is introduced**, on `build()`, on `reload()` and on
`store()`, since all three go through `SnapshotLoader.load`. A store is rejected before anything
is written.

**This is a breaking change against `0.1.0`.** A file that loads today stops loading if it
carries a stray key. The CHANGELOG reserves the right to break in a minor while the project is
`0.x`, and the break is the point rather than a side effect.

**It cannot ship without ADR-0055.** Putting an unknown key inside a block is legal today and
does nothing; making it an error without providing the declared place for application values
would remove a capability that exists without supplying the one that replaces it.

**Do not replace the recording wrapper with a declared set of key names.** It looks like the
simpler implementation and it reintroduces exactly the failure this decision exists to remove,
in a form no test would catch: the day someone adds a key to `fromBlock` and not to the list,
that key becomes unknown and every file using it is rejected.

**A file written for a later version fails on this one.** That is accepted. If it ever needs an
escape, a builder-level opt-out can be added without breaking anything, which is the direction
that stays open — the reverse would not have been.
