# ADR-0038: `snapshot()` gives callers the atomicity the swap already had

- **Status:** Accepted
- **Date:** 2026-08-26
- **Supersedes:** —
- **Amends:** [ADR-0012](0012-reload-atomicity-is-snapshot-wide.md) — the width at which the guarantee reaches a caller

## Context

[ADR-0012](0012-reload-atomicity-is-snapshot-wide.md) made reload atomicity snapshot-wide:
one staging area, one validation pass, one swap of one reference, so a partially applied
configuration never exists. That decision is correct and is not reopened here.

What it did not settle is **how much of that atomicity a caller actually receives**, and the
answer turned out to be less than the README claimed.

`LlmRegistry.get(String)` reads the published generation on every call — that is exactly what
makes a reload visible without a restart. It also means two consecutive calls are two
independent reads:

```java
ChatModel fast = registry.get("SL").chatModel();   // generation N
                                                   // <-- a swap may land here
ChatModel deep = registry.get("SH").chatModel();   // generation N+1
```

The swap is atomic. The *pair of lookups* is not, and nothing in the API let a caller ask for
one.

**Measured, not reasoned about.** A harness driving ~400 reloads at a 1 ms debounce while one
thread read the pair as fast as it could produced **220 mixed pairs in 111,597,529 reads** —
about two per million, on an AMD Ryzen 7 7840HS running Temurin 25. Rare enough never to
appear in a normal run of the `AtomicSnapshot` example, which is why the example reported
zero and the README said *"the mixed pair never appears"*.

That is the same shape of over-claim [ADR-0033](0033-provider-exceptions-pass-through-untranslated.md)
corrected for exceptions: a guarantee stated one notch wider than it holds. There it was
construction versus invocation; here it is the snapshot versus a sequence of lookups.

## Forces

- **The hazard is real and specific.** Multi-model councils are the case this library exists
  for ([ADR-0002](0002-scope-to-langchain4j-llm-configuration.md)). One model answering under
  the old configuration while its partner answers under the new is a correctness bug, and a
  two-per-million bug is worse than an obvious one because it will not appear in testing.
- **Against making `get()` itself transactional:** it cannot be. The caller decides where one
  unit of work begins and ends; the registry cannot infer that two calls belong together.
- **Against fixing it in documentation alone.** Narrowing the claim would be honest and would
  leave every caller to hand-roll the same workaround — take the map, hold it, look things up
  in it. That is a five-line answer the library should give once, correctly.
- **Against returning a bare `Map`:** it loses `UnknownConfigurationException` and hands back
  `null` for an unknown name, against the project's no-null discipline
  ([ADR-0006](0006-named-configurations-with-per-name-diffing.md) makes invalid states
  unrepresentable; a null lookup result walks that back).
- **In favour of an explicit handle:** it makes the boundary visible in the code. A reader
  seeing `registry.snapshot()` knows a consistency requirement is being expressed; a reader
  seeing two `get()` calls knows one is not.

## Decision

**Add `LlmRegistry.snapshot()`, returning an `LlmSnapshot`: one generation, held still.**

`snapshot()` performs exactly one read of the published generation. Everything obtained from
the result belongs to that generation, whatever reloads land afterwards. `LlmSnapshot` mirrors
the registry's own surface — `get(String)` throwing `UnknownConfigurationException`,
`names()`, and `contains(String)`.

`get()` on the registry keeps its current semantics unchanged and stays the primary API
([ADR-0009](0009-holder-api-primary-listeners-optional.md)): one lookup, always current. Its
Javadoc now states what it does not guarantee.

**A snapshot never updates.** It is taken per unit of work — per request, per council round —
and released. Holding one for the lifetime of the application re-creates the caching trap
`LlmBundle` warns about, in a new costume, and the Javadoc says so.

## Consequences

- Callers that need several models to agree can now say so, in one call, and get a guarantee
  rather than a probability.
- **The README's "never once" claim is corrected**, along with the `AtomicSnapshot` example,
  which now samples both ways and prints both columns — the `get()` pair and the snapshot
  pair — so the boundary is demonstrated rather than asserted.
- One new public type and one new method, permanently. The API is 0.x and this is an
  addition, so nothing breaks; it is still surface that has to be supported.
- **Do not "simplify" `snapshot()` away as a thin wrapper over the map.** Thin is the point:
  the single volatile read is the entire mechanism, and inlining it back into two `get()`
  calls restores the defect. Four tests in `ReloadTest` pin the behaviour — that a held
  snapshot keeps the old generation across a reload, that its names agree with each other,
  that it keeps a name a reload removed, and that it rejects an unknown one.
- The two-per-million figure is a property of this machine and this reload rate, not a
  constant. It is recorded to establish that the window is reachable, not to size it.
