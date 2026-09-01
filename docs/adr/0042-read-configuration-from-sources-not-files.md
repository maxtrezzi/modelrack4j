# ADR-0042: Read configuration from sources, not files, and let the application ask for a reload

- **Status:** Accepted — the write half widened by ADR-0044
- **Date:** 2026-08-31
- **Supersedes:** —
- **Amends:** —

## Context

Since M1 the registry has taken `List<Path>` and nothing else. `ConfigLoader` parses files,
`SnapshotLoader` holds paths, the builder takes paths, and the public `ReloadFailure` record
carries `List<Path>`. Watching is a single boolean on the builder, and it means one thing:
`WatchService` on the directories holding those files (ADR-0013, ADR-0024).

That was right while every consumer kept its configuration on disk. The consuming
application now needs configuration that may live in a database — a row of HOCON text rather
than a file — while still layering it over files. Two things follow. A database row has no
path, so it cannot be parsed by `ConfigFactory.parseFile`; and it cannot be watched by
`WatchService`, so the application, which knows when it changed, must be able to say so.

This ADR covers **reading only**. Writing configuration back — the feature this was raised
for — is a later task that builds on the same abstraction. It is separated because writing
against `Path` would have deepened the coupling this decision removes.

## Forces

**The filesystem had leaked into a public type.** `ReloadFailure` describes why a reload was
rejected. Nothing about that is file-shaped, yet its only identifying component was
`List<Path>`. A consumer reading from a database would receive an empty or invented list.

**An address is the wrong abstraction, and two attempts proved it.** The first proposal gave
each source an `Optional<Path>`, which put the filesystem back into the new interface one
level below where it had just been removed from four classes. The second, `Optional<URI>`,
is worse: an address forces the registry to dispatch on scheme, which is a standing
invitation to grow a handler per scheme — the boundary ADR-0002 draws against Apache Commons
Configuration. Neither buys anything, because the library never resolves the address: the
caller supplies the text.

**What the interface actually needed was notification, not location.** The only consumer of
the address was the code that decides what to watch. Generalised as an address the question
is ambiguous; generalised as "how do I learn this changed" it is exact — and the type already
existed unnamed, because `ConfigWatcher` is already `AutoCloseable` and already takes a
`Runnable onChange`.

**A label that is also an address fails silently.** Letting the identifier double as the
address was considered and rejected: `Path.of("llm_config#42")` is a valid relative path to a
file that does not exist. That is the failure shape this project has already paid for twice —
the resolved symlink ADR-0024 reversed, and the `<resources>` entry whose removal silently
empties `META-INF/services`.

**Per-source watching would undo ADR-0013.** Letting each source watch itself gives a clean
interface and N threads and N `WatchService` instances for N files in one directory. The
present design groups the deduplicated parent directories behind one of each, and that
grouping is the part of the watcher that was hardest to get right.

**A public reload has a caller; the watcher thread does not.** ADR-0028 justifies core's
dependency on `slf4j-api` precisely because a rejected reload on the watcher thread has
nobody to throw to. An application that has just written a database row and asks for a reload
does have somebody, and making it register a listener to learn whether a synchronous call
worked is a poor API.

**The single-writer invariant does not survive, and it dies here rather than later.**
`LlmRegistry.reload()` reads the live map, spends real time parsing, validating and building,
then writes. It needs no lock today only because one thread — the watcher's — ever runs it. A
trigger the application can call breaks that on the read side alone, before any write API
exists.

## Decision

**A configuration layer is a `ConfigSource`: an identifier and its text.**

    public interface ConfigSource {
        String id();
        String text();
    }

`id()` is a stable, human-readable label. It appears in parse errors, through
`ConfigParseOptions.setOriginDescription`, and in `ReloadFailure`. **It is not an address:
the library never interprets it**, and it must not carry secrets, because it is logged. Ids
must be unique across the sources of one registry; `build()` rejects duplicates.

`text()` returns the layer's HOCON text, unresolved, and **is called on every reload** — an
implementation that queries a database each time is behaving correctly, and one that caches
its text will never see a change.

Nothing in either method names a file, a path or a URI.

**Change notification is a separate, optional collaborator.**

    public interface ChangeNotifier extends AutoCloseable {
        void start(Runnable onChange);
    }

The file implementation is today's `ConfigWatcher` behind that interface, constructed once
over the whole list of files, so the directory grouping, the debounce and the re-registration
of ADR-0013 and ADR-0024 are unchanged. A source with no notifier is simply not watched, and
the application calls `reload()` instead. An application that wants notification the library
does not provide — a database `LISTEN/NOTIFY`, a Kubernetes informer, a queue — implements
this interface without the library learning any scheme.

**`ConfigLoader` parses text, not files.** `ConfigFactory.parseString(source.text(),
ConfigParseOptions.defaults().setOriginDescription(source.id()))` replaces `parseFile`. The
merge order and the **single `resolve()` on the merged result** of ADR-0007 are untouched;
that rule always spoke of layers, never of files.

**Reload has two doors and one corridor.**

    public  Optional<ReloadChange> reload();   // returns the diff, empty if nothing changed; throws if rejected
            void reloadQuietly();              // for the notifier thread: logs and notifies, never throws
    private synchronized ... doReload();       // the work, in one place

Both notify the registered listeners: listeners exist for other components, a return value
and an exception exist for the caller. The private method is `synchronized`, so reloads
serialise. Readers do not take that lock — `get()` and `snapshot()` remain a volatile read.

**`ReloadFailure` carries `List<ConfigSource>` instead of `List<Path>`**, so a consumer gets
its own source objects back, with whatever typed fields it gave them.

The builder keeps `configFiles(List<Path>)` as the shorthand for the common case: it builds
the file sources *and* retains the typed paths for `watch(true)`. `sources(List<ConfigSource>)`
is the general form. `watch(true)` without file sources fails at `build()` with a message
naming the alternatives, rather than watching nothing in silence.

## Consequences

**Core no longer assumes the filesystem.** `java.nio.file` survives in exactly two concrete
classes, the file source and the file notifier, and in neither interface. Mixed layering —
a base file plus a database override — needs no special case.

**A public record changed shape.** `ReloadFailure` is a breaking change for any consumer
that already destructured it. This is free at `0.1.0-SNAPSHOT` with no tag and no published
artefact, and stops being free at M6, whose preparation is running in parallel. That timing
is the reason this is done now rather than after a release.

**Reloads serialise, and that is a behaviour change.** A manual reload waits for a watcher
reload already in flight. Without the lock, two threads reading the same previous snapshot
would both build and both publish, and the later write would discard the earlier one in
silence while its listeners had already announced it. Do not "simplify away" the
`synchronized`, and do not widen it to the readers: the guarantee in ADR-0038 comes from the
volatile publication, not from mutual exclusion, and taking a lock in `get()` would cost every
caller for nothing.

**A rejected reload is now reported three times** — thrown to the caller, delivered to the
failure listeners, and logged under ADR-0031. That is accepted deliberately: the three
audiences are different, and dropping the log would leave a `reloadQuietly()` failure
traceless, which is the situation ADR-0031 exists to prevent.

**`ConfigSource` is not a functional interface**, having two abstract methods. Static
factories `ConfigSource.of(id, text)` and `ConfigSource.ofFile(path)` cover the common cases;
`of` freezes the text it is given, so a source that must be re-read implements the interface
instead.

**`ChangeNotifier` and `ConfigSource` do not belong in the `spi` package.** That package is
documented as the provider SPI discovered through `ServiceLoader`, and these two are passed
to the builder by hand. Filing them there would promise a discovery mechanism that does not
exist for them.

**A file layer is still parsed as a file, and that is not an implementation detail.**
`include "sibling.conf"` in HOCON resolves relative to the file that contains it, and only
`ConfigFactory.parseFile` knows which file that is. Parsing a file's bytes as text moves the
includer to the classpath, and since an include is allow-missing by default the included
block then disappears **with no error of any kind**. So `ConfigLoader` parses a file source
with `parseFile` and everything else with `parseString`. The `instanceof` that chooses
between them is the one place the loader knows what a source is, and it is deliberate: an
include is a directive about a *directory*, and a layer with no directory of its own has no
answer to give. A source that is not a file therefore gets Typesafe Config's documented
behaviour for text, which is to look an include up on the classpath.

**No new dependency.** `parseString` and `setOriginDescription` are already in the
`com.typesafe:config` artefact core declares, so ADR-0005's four compile dependencies stand.

**What this forecloses.** The library will not gain per-scheme source handling: no `jdbc:`,
no `http:`, no classpath URLs. An application that can produce a string can already be a
source, and one that cannot is not made possible by the library learning its address format.
