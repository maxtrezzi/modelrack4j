# ADR-0053: A separate exception for a layer that cannot be reached

- **Status:** Accepted
- **Date:** 2026-09-04
- **Supersedes:** —
- **Amends:** —

## Context

`ConfigValidationException` has meant two unrelated things since M1. "Your text is wrong" — a
malformed block, a value out of range, an unresolved substitution, a provider that refuses its
configuration — and "the medium failed" — the file is missing, the directory is not writable,
the disk is full. The javadoc on `store` said both in one line: *"if the text does not parse or
does not validate, or if it cannot be stored"*.

The two need different answers. Invalid text has to be corrected and will fail again
identically; an unreachable layer may work on the next attempt and has nothing to correct. An
application that turns the library's exceptions into HTTP responses has to choose `400` or
`500` from one type, and is wrong for one of the two cases — a full disk reaching a client as
*"your configuration is invalid"*, which it is not.

This was raised as D6 by the first consumer putting `0.1.0` behind HTTP, alongside
[ADR-0052](0052-no-version-token-the-expected-text-is-the-token.md). `0.1.0` is published, so
this is a breaking change to a permanent artifact; the CHANGELOG reserves that right in a minor
while the version is `0.x`.

## Forces

**The read side has the same mislabel as the write side.** D6 was raised about `store`, but a
missing file at `build()` has always been reported as a validation failure too. Fixing only the
write half would leave the library with two answers to the same question, and the type would
have had to be named for storing rather than for what it means.

**Subclassing loses the property the change is for.** `ConfigAccessException extends
ConfigValidationException` breaks nothing: every existing catch keeps working. It also asserts
that a full disk *is a* validation failure, and an application that has not been updated goes
on answering `400` for it — which is exactly the defect. The compatibility it buys is
compatibility with the bug.

**Leaving it costs nothing today and cannot be undone later.** One type is one thing to catch,
and 0.x churn on a published artifact has a real price. Against that: every month this waits,
more code catches the single type, and the change gets more expensive rather than less. The
consumer that raised it needs the distinction now.

**Not every failure to store is about the medium.** `WritableFileConfigSource` refuses a layer
reached through a symbolic link that crosses directories, because an `include` would then
resolve differently during validation than afterwards. Nothing failed there — the library
declines a combination it cannot validate honestly, which is a statement about the text.

## Decision

**A new public unchecked `ConfigAccessException`, not a subclass of
`ConfigValidationException`.** It means: the layer could not be reached, so nothing was learned
about the configuration. Both types keep the same two constructors.

It is thrown from five places, on both sides of the boundary:

| Where | Why |
|---|---|
| `FileConfigSource.read` — not readable | the file is missing or unreadable |
| `FileConfigSource.read` — `IOException` | the read itself failed |
| `WritableFileConfigSource.stage` — no parent directory | there is nowhere to write |
| `WritableFileConfigSource.stage` — `IOException` | the staged file cannot be written |
| `WritableFileConfigSource.commitStaged` — `IOException` | the move onto the destination failed |

and from `ConfigLoader.load`, which now catches `ConfigException.IO` before `ConfigException`.
That distinction is the loader's whole read path for a file layer: a file layer is parsed with
`parseFile` rather than through `text()` (ADR-0042), so a missing file never reaches
`FileConfigSource.read` during a load. Typesafe Config reports a missing or unreadable file as
`ConfigException.IO` and a syntax error as `ConfigException.Parse`, so the two separate exactly
where they should.

**Three sites deliberately stay `ConfigValidationException`:** the symlink-and-`include`
refusal above; `LlmRegistry.requireOwnLayer`, where naming a layer the registry does not hold
is a programming error caught before anything is attempted; and a source whose `text()` returned
`null`, which is a broken implementation rather than a failed medium.

The SPI contracts change with it: `ConfigSource.text()` and `WritableConfigSource.write(String)`
document the new type as what an implementation should throw when its medium fails.

## Consequences

**It is a breaking change, and the migration is one extra catch.** Code catching
`ConfigValidationException` around `build()`, `reload()`, `store()` or `storeIfUnchanged()`
that wants the previous behaviour catches both. Nothing fails to compile: the change is in
which type is thrown, not in any signature.

**A third-party `ConfigSource` that still throws the old type keeps working.** Both are
unchecked and both propagate the same way; `LlmRegistry.reload()` catches `RuntimeException`
and `ReloadFailure.cause` is typed `Exception`, so the failure path needed no change at all.
Such a source only loses the distinction for its own callers.

**Rollback is unchanged.** A store that fails while writing still puts the previous snapshot
back before the exception leaves the method, and still publishes nothing and notifies nobody.
Only the type of what leaves the method is different.

**The line is now the thing to maintain, not the list.** A new throw site belongs to
`ConfigAccessException` when the library learned nothing about the configuration because it
could not reach it, and to `ConfigValidationException` when it read something and objected. Do
not add a site to the first because it happens to involve a file: the symlink refusal involves
a file and belongs to the second.

**Do not make one a subclass of the other later.** That would restore the ambiguity this ADR
removes, silently — every caller would keep compiling and start answering the wrong thing
again. A test asserts the two are unrelated so that the reversal fails loudly.
