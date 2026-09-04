# ADR-0051: A layer answers for itself, adapted at the boundary

- **Status:** Accepted
- **Date:** 2026-09-04
- **Supersedes:** —
- **Amends:** ADR-0050

## Context

`ConfigSource` is `id()` plus `text()` and names no file
([ADR-0042](0042-read-configuration-from-sources-not-files.md)). The library still needs two
answers a source does not give: how to parse a layer, because a file must be parsed through
the file for its `include` to resolve beside it, and what to watch for a layer, because only
a file can be watched.

Both were recovered the same way, with `instanceof FileBacked` — once in `ConfigLoader.parse`
and, since [ADR-0050](0050-watch-the-file-layers-whichever-method-supplied-them.md), once in
`LlmRegistry.Builder.chooseNotifier`. One test asking a question is a check. The same test
repeated at every use site is a type switch, and it grows by one every time a third question
appears.

## Forces

**The obvious fix is the one ADR-0042 refused, twice.** Putting the answer on the public
interface — `Optional<Path>`, `Optional<URI>`, or a `MonitorableConfigSource` that returns a
`ChangeNotifier` — was considered when sources were introduced and rejected: an address in the
interface puts the filesystem back where it had just been removed from, and a notifier per
source undoes ADR-0013's single watch service over the deduplicated set of parent directories.
Nothing about that has changed.

**Polymorphism on the public type costs the public type.** `ConfigSource` is implemented by
applications. Giving it `parse(ConfigParseOptions)` would take the interface from two methods
to three and drag `com.typesafe.config` into the SPI, so every application-written source
would have to implement HOCON parsing. On a published artifact that is a much worse trade than
the type switch it removes.

**Java 17 does not pay for elegance here.** The clean form of a closed-set dispatch is a
`sealed` type with an exhaustive pattern `switch`, which is Java 21. The floor is 17
([ADR-0019](0019-target-java-17.md)), and the project has already met this wall:
`MemoryConfig` is `sealed` and `SnapshotLoader` still ends its `instanceof` chain with a
throwing default, because the compiler checks nothing.

**But the repetition is removable without any of that.** The question is answered once per
layer, at a boundary that already exists — every layer enters the registry through
`Builder.build()`. What is missing is somewhere to keep the answer.

## Decision

**An internal `Layer` type, created once at the boundary, that the use sites ask instead of
inspecting.**

    sealed interface Layer permits FileLayer, TextLayer {
        ConfigSource source();
        Config parse(ConfigParseOptions options);
        Optional<Path> watchTarget();

        static Layer of(ConfigSource source) { ... }   // the only place that asks
    }

`FileLayer` parses through `parseFile` and answers its configured path; `TextLayer` parses its
`text()` and answers nothing. `ConfigLoader`, `SnapshotLoader` and `LlmRegistry` carry
`List<Layer>` and call methods; `Layer.sourcesOf` unwraps where a public type needs the
`ConfigSource` back, which is `ReloadFailure` and the message of `requireOwnLayer`.

`FileBacked` stays package-private and becomes `sealed`, permitting the three records that
implement it. It is now read in exactly one place, `Layer.of`.

**The public API does not change at all**, which is the point: no new interface, no new
method, no new type an application can see. There is no CHANGELOG entry for this, deliberately.

## Consequences

**Two type tests become one, and it is at a boundary rather than at a use site.** A third
question about a layer — should one appear — is a method on `Layer` and an answer in two
records, not a third `instanceof` somewhere else.

**The staging dispatch is deliberately left alone.** `StagedWrite.prepare` tests
`instanceof WritableFileConfigSource`, and that is a different question: not "is this a file"
but "is this writable target a file". It is already a single factory rather than a repeated
test, and folding it into `Layer` would need either a third variant that exists only to throw
for a read-only file layer, or a cast. One localised dispatch is better than an unreachable
branch.

**This is the shape to reach for again.** When the library needs to know a kind of thing about
a layer, adapt at the boundary into the internal type; do not add a marker to the public
interface and read it at the point of use. The marker is what makes every reader responsible
for knowing what to do with it.

**When the floor moves to 21**, `Layer.of` becomes a pattern `switch` the compiler checks for
exhaustiveness, and adding a variant then fails at compile time rather than falling through.
That is an improvement to one method, not a reason to revisit this decision.

**What this does not do.** An application's own `ConfigSource` that reads a file is still a
`TextLayer`: it cannot declare a file, so it is not parsed as one and not watched. That limit
is ADR-0050's and is unchanged — moving the answer inside does not make an outside source able
to give it.
