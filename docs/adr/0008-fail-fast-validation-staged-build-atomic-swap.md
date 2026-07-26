# ADR-0008: Fail-fast validation, staged build, atomic swap

- **Status:** Accepted — swap scope widened by [ADR-0012](0012-reload-atomicity-is-snapshot-wide.md)
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

> This record established the staged-build discipline. ADR-0012 later widened the swap
> from per-bundle to whole-snapshot. Where the two differ, **ADR-0012 wins**.

## Context

The library sits in the request path of a running application and rebuilds live objects in
response to file edits. That makes an ordinary configuration typo a potential production
incident, and it makes the ordering of parse, validate, build, and publish a correctness
question rather than a stylistic one.

## Forces

- A typo must never take down live traffic. The reload mechanism is only worth having if
  a bad edit is strictly safer than no reload at all.
- Editors and deployment tools write files non-atomically — partial writes, or
  temp-file-then-rename. A watcher will therefore observe torn and spurious states.
- `validate()` cannot catch everything. Builders throw for reasons only construction
  reveals: a malformed base URL rejected when the HTTP client is created, an unusable
  parameter combination. Validation reduces the failure rate; it does not eliminate it.
- Consequently, "validate then build in place" is not safe. Building in place means the
  first failing bundle leaves the registry half-updated.

## Decision

Debounce watch events (~300 ms) so a burst of writes produces one attempt. Then:
parse → validate → build **every changed bundle into a staging area** → publish by
swapping the snapshot reference in a single operation.

Any failure at any stage leaves the previous state untouched and live, and reports through
`onReloadFailure`. Configuration records validate in their constructor or loader, so an
invalid configuration object cannot exist.

## Consequences

- The application can never observe a half-consistent or broken state, however badly the
  file was written.
- **Do not replace staged-build with build-in-place "for simplicity".** The staging step
  is load-bearing precisely because validation cannot catch everything, and removing it
  reintroduces the failure it prevents. This is the single most likely well-meaning
  regression in the codebase.
- A failed reload is silent unless the application subscribes to failures, so the failure
  callback and its logging are part of the contract, not an extra.
- The debounce adds latency between edit and effect, bounded and deliberate.
