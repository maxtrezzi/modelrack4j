# ADR-0007: Layered HOCON via Typesafe Config as a core dependency

- **Status:** Accepted
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

Deployments are layered: library defaults, then product configuration, then per-customer
customisation, each overriding the last. Supporting that means choosing a configuration
format and — more consequentially — owning or borrowing its merge semantics. For a
published library, any dependency taken here is imposed on every consumer permanently.

## Forces

- Hand-rolled merge semantics are a known graveyard. Deep-merge versus block-replace, how
  lists combine, whether a key can be removed by a higher layer — every one of these needs
  specifying, testing, documenting, and defending against reasonable people who expected
  the other behaviour.
- Secrets need environment substitution, and the failure mode matters: a missing key must
  fail loudly at load rather than silently producing an unauthenticated client.
- "Which file set this value?" is the first question asked when layered configuration
  misbehaves. Answering it requires provenance tracking, which is a subsystem in itself.
- Against all that: adding a dependency to a published library is close to irreversible.
- Typesafe Config is small, plain Java, and has no transitive dependencies of its own —
  the cheapest possible version of that cost.

## Decision

Take `com.typesafe:config` as a core dependency and use HOCON. It supplies documented
merge semantics via `withFallback`, `${ENV_VAR}` substitution with a mandatory form that
fails on absence, and `origin()` provenance — each of which would otherwise be built and
maintained here.

Layer order is **explicit**, given as an ordered list through the API. No classpath
scanning, no filename conventions.

All files merge into a single in-memory snapshot before anything is built.

## Consequences

- One dependency imposed on consumers; in exchange, no invented semantics to maintain or
  argue about, and a format users may already know.
- Provenance is available for a debug API answering "which file supplied this value?".
- Explicit ordering keeps "which layer won?" answerable. Convention-based discovery would
  make it a guess, which is exactly the confusion this library exists to remove.
- The single merged snapshot means reload atomicity does not depend on file count, and a
  one-file setup is the degenerate case of the same code path rather than a special one.
- **A trap that must not be simplified away:** Typesafe Config separates parsing from
  resolution. Each layer is parsed only; layers are merged; `.resolve()` is called exactly
  once on the merged result. Resolving per file breaks mandatory substitution — a `${VAR}`
  in a lower layer fails even when a higher layer overrides that key outright, and
  cross-layer references never see merged values. This needs a dedicated regression test:
  a defaults layer referencing a variable that a higher layer overrides.
