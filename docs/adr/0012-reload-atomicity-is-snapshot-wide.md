# ADR-0012: Reload atomicity is snapshot-wide, not per-bundle

- **Status:** Accepted — the width reaching a caller amended by [ADR-0038](0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md)
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —
- **Amends:** [ADR-0008](0008-fail-fast-validation-staged-build-atomic-swap.md) — widens its swap scope from per-bundle to whole-snapshot

## Context

ADR-0007 merges all configuration files into one in-memory snapshot, and ADR-0008 builds
changed bundles in staging before publishing them. What remained unsettled was the unit of
publication: one bundle at a time, or the whole snapshot at once — and, following from
that, the shape of the reload callback.

## Forces

- A per-bundle callback (`onReload(name, oldBundle, newBundle)`) reintroduces exactly the
  tearing the merged snapshot was designed to prevent. A single edit touching two named
  configurations fires twice, and between the two callbacks the application observes the
  new version of one alongside the old version of the other.
- For the motivating use case — several models cooperating on one problem — a mismatched
  pair is a genuine correctness hazard, not a cosmetic inconsistency. An edit that changes
  a shared instruction across all of them, applied to half of them, produces incoherent
  behaviour that is hard to attribute.
- Against snapshot-wide publication: one invalid block then blocks updates to every other
  block, including blocks that were edited correctly in the same save.
- But a configuration file is authored and saved as a unit. Partial application is the more
  surprising outcome, and it leaves the running system in a state matching no file the
  user ever wrote.

## Decision

One snapshot, one atomic swap, one callback.

`onReload(change)` fires exactly once per successful swap, carrying `updated`, `added`,
and `removed` name sets. If any named block fails to parse, validate, or build, **nothing**
is swapped: the entire previous snapshot stays live and `onReloadFailure` fires exactly
once.

Per-bundle notifications are derivable from the change object. They are never fired
independently.

## Consequences

- A strictly stronger guarantee than per-bundle publication, with a simpler mental model
  and a simpler implementation — there is exactly one reference to swap.
- The application can never observe a mixed-generation set of bundles.
- Accepted cost: one bad block holds back every good one in the same reload. This is
  deliberate, and the failure callback exists so the situation is visible rather than
  silent.
- The callback contract is snapshot-level in the public API. Reintroducing a per-bundle
  listener later would reintroduce the tearing, so it is not a future enhancement.
