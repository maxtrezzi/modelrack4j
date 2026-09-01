# ADR-0044: Store a layer back as text, validated before it is stored

- **Status:** Accepted
- **Date:** 2026-09-01
- **Supersedes:** —
- **Amends:** [ADR-0042](0042-read-configuration-from-sources-not-files.md) — the write half of the same abstraction

## Context

[ADR-0042](0042-read-configuration-from-sources-not-files.md) made a layer a `ConfigSource`:
an id and its text, naming no file, path or URL. It answered the reading half of a request
whose other half is writing. An application that lets its users choose a model has to save
that choice somewhere, and the layer it belongs in is one the application owns.

P19 left exactly one route for that: write the layer yourself, then call `reload()`. The two
steps are in the wrong order. If the new text does not validate, the reload rejects it — but
the layer already holds it, so the configuration is broken on disk and the next start fails.
The application is told about the problem after it has committed it.

Nothing about the library forced that order. The registry already parses, validates and
builds every changed bundle in a staging area before publishing anything
([ADR-0012](0012-reload-atomicity-is-snapshot-wide.md),
[ADR-0038](0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md)). What was
missing was a way for a caller to put its own text through that machinery *before* the text
became permanent.

## Forces

**A structured edit API was built first, and lost.** The first implementation was a fluent
per-key editor — `registry.edit(layer).set("SL.model-name", "…").remove("CR").commit()` — 302
lines with 440 lines of tests, reviewed and corrected. It worked. Four things it had to do
came from the same root, and all four disappear when the application supplies the text:

- **It had to decide which keys to write**, and wrote only the keys named. That is the
  correct answer — writing a whole block into a higher layer copies down every value the
  block inherited and freezes it there, silently, because the result is still valid — but
  from outside the API it is indistinguishable from a save that dropped things.
- **It rewrote the layer from its parsed form, so an `include` could not survive it.** Parsed
  as text the directive resolves to nothing and renders away; parsed through the file its
  contents are inlined and frozen. Any layer holding one had to be refused outright.
- **It could not tell the string `"${X}"` from the substitution `${X}`**, so `set` needed a
  second method, `setSubstitution`, and the caller had to know which one it meant.
- **It canonicalised the layer**: comments and substitutions survived, key order and
  alignment did not.

**What the library alone can do is the order, and it is not reproducible from outside.**
Validating the whole configuration against text that has not been stored yet needs a staging
area; undoing a publication needs the previous snapshot. An application has neither.

**Text is the only representation that keeps a secret out of the layer.** An application
holds *resolved* values, so an API that took a configuration object back would write the
resolved secret into the file. Measured: `api-key = ${?HOME}` in the layer text, and
`/home/…` from `config.apiKey()`.

**One writer is a different problem from two.** A single store can be made atomic against
reloads and against other stores. It cannot hold the caller's *read* and its own *write*
together: two threads that both read the layer and then store lose one of the two changes.
Measured inside the edit API before it was fixed there: two concurrent commits lost one
change in 199 of 200 rounds.

Against all of that: handing the text back to the application also hands it the freeze trap
named above. That is the price, and it is paid in documentation rather than in API.

## Decision

**The registry stores text.** `LlmRegistry.store(WritableConfigSource, String)` takes the
layer's whole new content and returns what changed. The library has no opinion about what the
text says; it has an opinion about when it becomes permanent.

**The order is the contract.** Stage the text without storing it; validate the entire
configuration with the staged copy in place of the layer; publish; store. If storing fails,
put the previous snapshot back and throw. Nothing is published if validation fails, and no
listener runs for a store at any point — the caller made the change and is given it back as a
return value. Publishing *before* storing is what leaves a waking file watcher an empty diff,
so no flag is needed to suppress the event.

**Writability is a separate capability.** `WritableConfigSource extends ConfigSource` adds
`write(String)`, and `ConfigSource.ofWritableFile(Path)` provides it for a file. A layer that
was never made writable cannot be named as a target. An implementation must store nothing at
all if it throws, because the rollback assumes exactly that.

**Two writers get a compare-and-set.** `storeIfUnchanged(target, expected, text)` re-reads the
layer inside the reload lock and stores only if it still holds `expected`, character for
character; otherwise it throws `StaleLayerException`, which carries the layer's current text
to rebase onto. The comparison is on the text and not on its meaning: a layer somebody
reformatted or commented is a layer that moved. `StaleLayerException` is deliberately **not**
a `ConfigValidationException` — a lost race is retryable by the program, a validation failure
is for a person to read.

**A file is written through a staged file beside its destination**, moved onto it, with the
destination resolved once and carried to the commit, the target's permissions copied onto the
staged file, and a symbolic link followed rather than replaced
([ADR-0024](0024-watch-the-symlink-s-directory-not-its-real-path.md)).

**`include` keeps working, with one refusal.** The staged file sits beside the destination so
an include in the new text resolves during validation the way it will resolve afterwards. The
refusal is narrow: a layer reached through a symbolic link into *another* directory, where the
staged copy and the running layer would resolve the include in two different directories.
Measured with `config-1.4.9`: parsing through the link finds the sibling next to the link,
parsing the staged copy finds the one next to the target.

## Consequences

**Gained.** An application can offer a configuration change and have it refused before it is
saved. The write half of ADR-0042 works for any medium, not just files. A store raises no
reload event without a flag arranging it, and a rejected store tells its caller and nobody
else.

**Accepted.** The application owns the read-modify-write. It must produce the layer's whole
text, and must not copy into it the values the layer inherits from below — the freeze trap the
edit API used to prevent, now a documented rule instead of an enforced one. And the library
cannot tell a deliberate removal from an accident: a new text that drops an `include` the
configuration did not strictly need is stored, and the values it brought are gone. Validation
only asks whether the result loads.

**Do not simplify these.** The include check belongs to the *validating* path
(`StagedFileWrite`), not to `stage()`, because a plain `write(String)` validates nothing and
must not inherit a refusal that only protects validation. The destination is resolved once:
resolving it again at commit time lets a ConfigMap swap the link in between, so the staged
file is written beside one directory and moved onto another, carrying permissions from a file
it no longer replaces. And the compare-and-set compares text, not parsed meaning — comparing
meaning would let a comment somebody wrote on purpose disappear under a concurrent store.

**Foreclosed.** A structured per-key edit API is not coming back without an ADR that answers
the four problems above. The read-modify-write loop belongs to the application, and
`storeIfUnchanged` is what makes it safe rather than an API that hides it.
