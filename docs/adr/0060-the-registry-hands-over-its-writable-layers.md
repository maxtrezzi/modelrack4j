# ADR-0060: The registry hands over its writable layers

- **Status:** Accepted
- **Date:** 2026-09-07
- **Supersedes:** —
- **Amends:** —

## Context

An application that lets a user edit configuration has to name the layer it may write.
`LlmRegistry.store(WritableConfigSource, String)` takes that layer, and
`ConfigSource.ofWritableFile(Path)` makes one, so the application can keep the reference it
built beside the registry.

ADR-0054 argued against that. It added `sources()` so the layers would be reachable from the
registry rather than from a field an application has to remember to keep, and its javadoc says
so: *use it to find the layer to write instead of keeping the reference beside the registry*.
It then made the reader do the finding, and shipped the code for it as the documented example:

```java
WritableConfigSource userLayer = registry.sources().stream()
        .filter(WritableConfigSource.class::isInstance)
        .map(WritableConfigSource.class::cast)
        .findFirst()
        .orElseThrow();
```

The consuming application wrote that filter twice and asked for it to stop being necessary. The
friction is one this library's own documentation created: it recommends a lookup and provides no
way to perform it.

## Forces

**`Optional<WritableConfigSource>` reads best and is a lie.** `sources(...)` accepts any number
of layers and says nothing about how many may be written, so two writable layers is a
configuration the library allows. An `Optional` then has to pick one — arbitrary — or throw,
which turns a legal configuration into a failure at the moment somebody asks a question.

**A list reads worse in the case everybody has.** Almost every application configures exactly
one writable layer, and `writableSources().get(0)` is clumsier than an `Optional` would be.

**Both, and the API grows.** An `Optional` accessor beside the list, throwing when there are two,
is two methods and a rule to remember for a saving of four characters at one call site.

**Doing nothing is defensible and was the starting position.** The filter works, and this is
public API on a `0.x` release that has already taken one source break (ADR-0059). What moved it
was that the omission is not neutral: the library tells applications to look the layer up.

## Decision

`LlmRegistry.writableSources()` returns `List<WritableConfigSource>` — the layers among
`sources()` that can be written, in the same order, unmodifiable, possibly empty.

- **A list, not an `Optional`.** The library does not know which of two writable layers a caller
  meant, and will not guess. An application that configures one takes the first, which is a fact
  about that application rather than about the registry.
- **The order is `sources()`'s order**, lowest precedence first. With two writable layers it is
  the only thing that distinguishes them, so it is contract rather than an accident of
  iteration.
- **Empty is an ordinary answer**, not a failure: a registry whose every layer is read-only is
  the common case and stays legal.
- **Computed once, in the constructor.** The layers are fixed at `build()`, the same reason
  `Layer.sourcesOf` is called there (ADR-0051).
- **`sources()` keeps returning everything**, and its javadoc now points here instead of
  carrying the filter.

## Consequences

**The documented advice becomes performable.** ADR-0054's argument — the layers belong to the
registry, not to a field beside it — now costs one call rather than five lines.

**What is handed back is what `store()` accepts.** `requireOwnLayer` compares by record equality,
so this returns the layer objects the registry was built with, not copies. A test pins that,
because a convenience that returned equal-but-rejected layers would be worse than no convenience.

**It stays a filter over a list the caller can also get.** Nothing here is reachable only through
this method, so an application with an unusual arrangement — several writable layers, chosen by
`id()` — is no worse off than before.

**Two writable layers remain the caller's problem, deliberately.** The library refuses to name
one of them "the" writable layer. If a future decision wants that, it needs a rule for choosing —
highest precedence, or a name given at build time — and that rule is what this ADR declines to
invent before anyone needs it.
