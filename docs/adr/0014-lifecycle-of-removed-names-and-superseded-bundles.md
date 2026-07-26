# ADR-0014: Lifecycle of removed names and superseded bundles

- **Status:** Accepted
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

Reload changes what exists, not just what values are set. Two lifecycle questions follow
and neither has a default that can be left implicit: what happens to a named
configuration that disappears from the file, and what happens to the objects a reload
replaces.

## Forces

**Removed names.** If a bundle lingers after its block is deleted, the registry silently
disagrees with the configuration file — the precise confusion this library exists to
prevent. If it vanishes, code that was working stops working, but visibly and for a
reason the user caused.

**Superseded bundles.** Each model instance holds an HTTP client, so never releasing them
leaks slowly under repeated reloads. But disposing them eagerly at swap time would break
in-flight requests that legitimately still hold the previous instance — turning a
configuration edit into failed user requests, which is worse than the leak.

There is a middle option — dispose after a grace period — but it needs a timeout nobody
can pick correctly without evidence, and it can still cut off a slow request.

## Decision

**Removed names are honoured.** The name disappears from the registry, subsequent `get()`
throws `UnknownConfigurationException`, and the removal is reported in the change object's
`removed` set (ADR-0012). Names added by a reload become available immediately and are
reported in `added`.

**Superseded bundles are not closed in v1.** LangChain4j model instances are immutable and
in-flight calls complete normally; garbage collection reclaims them once no caller holds a
reference. Document that each model holds an HTTP client and that pathologically frequent
reload is not a supported use case.

## Consequences

- The registry always mirrors the configuration file, with no divergence to reason about.
- Removing a name in production breaks callers of that name promptly and loudly, which is
  the intended trade — silent staleness is the worse failure.
- Accepted risk: an application that reloads in a tight loop **and** retains bundle
  references could accumulate HTTP clients. Revisit with explicit disposal and a grace
  period only if this is observed in practice, not pre-emptively.
- The don't-cache guidance from ADR-0009 does double duty here — callers who follow it
  also let superseded bundles become collectable promptly.
