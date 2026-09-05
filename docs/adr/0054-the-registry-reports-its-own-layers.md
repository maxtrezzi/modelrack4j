# ADR-0054: The registry reports its own layers

- **Status:** Accepted
- **Date:** 2026-09-05
- **Supersedes:** —
- **Amends:** —

## Context

`LlmRegistry.store(target, text)` takes the layer to write as an argument, and
[ADR-0042](0042-read-configuration-from-sources-not-files.md) makes that layer a `ConfigSource` the
application constructed itself. Until now the registry gave no way to ask for it back, so an
application that wanted to store had to keep the `WritableConfigSource` reference beside the
registry for as long as the registry lived, and pass both wherever either was needed.

The consuming application reported this after upgrading to the current development version.
It is not blocking there — it keeps the reference — but it is the first thing somebody writing
this code meets, and the workaround is a second field holding something the registry already
has.

The layers are not in fact private. `ReloadFailure` is a public record whose first component
is `List<ConfigSource> sources`, so every layer, writable ones included, is already handed to
any `onReloadFailure` listener.

## Forces

**Encapsulation was the argument for keeping them in, and it no longer holds.** The concern is
real: a caller holding a `WritableConfigSource` can call its `write(String)` directly, which
stores text nothing validated and leaves the registry serving the previous configuration —
exactly the order [P20](../tasks/post-v1.md#p20--writing-a-configuration-layer-back) exists to
prevent. But `ReloadFailure` already hands the same objects out. Withholding an accessor
protects nothing; it only decides *which* path an application discovers them on, and the
failure-listener path is the one an application is least likely to have written.

**The alternative of reporting less.** A `List<String> sourceIds()` would serve diagnostics
and could not be misused, because an id is a label rather than a handle. It also would not
solve the problem that was reported: an id cannot be passed to `store`. Naming a layer by id
and having the registry look it up was the other shape considered, and it moves the same
capability behind a lookup that can fail at runtime, for no gain.

**Immutability makes the accessor honest.** The layers are fixed at `build()`: a reload
re-reads the same sources rather than replacing them. So the list is a property of the
registry, not a snapshot of changing state, and returning it needs none of the generation
reasoning that [ADR-0038](0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md) applies to
bundles.

## Decision

`LlmRegistry.sources()` returns the layers the registry was built from, lowest precedence
first, as an unmodifiable list.

Its documentation states the rule the accessor cannot enforce: a writable layer found this way
is written through `store(...)` or `storeIfUnchanged(...)`, never through its own
`write(String)`.

## Consequences

An application can find its writable layer from the registry, and the two no longer have to
travel together. The example in the method's javadoc — filter the list for
`WritableConfigSource` — is the intended use and is covered by a test that runs it.

`ReloadFailure.sources()` and `LlmRegistry.sources()` must keep reporting the same layers. A
test asserts they are equal, so a future change that makes one of them report something
derived cannot pass unnoticed.

The misuse this opens is `registry.sources()` followed by a direct `write(String)`. It was
already reachable through a failure listener, so this is a wider door rather than a new one,
and only documentation closes it. Anything stronger — a wrapper type that hides `write`, or a
registry that refuses to hand back writable layers — would have to change `ReloadFailure` too,
and would make an application's own `ConfigSource` implementation less useful than the ones
this library ships, which is what ADR-0042 refused.
