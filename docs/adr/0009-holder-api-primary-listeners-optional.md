# ADR-0009: Holder API primary, listeners optional

- **Status:** Accepted
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

Two API shapes are available for delivering reloaded objects to an application: a holder
the caller reads from on each use, or a listener the library calls on change. The choice
determines who is responsible for swapping references safely.

## Forces

- **Listener-only** pushes the hard part back onto every user: each application must
  store the new objects somewhere, publish them safely across threads, and coordinate
  with in-flight work. That is precisely the burden the library exists to absorb, and
  every consumer would solve it again, differently.
- **Holder-only** leaves no hook for the things applications legitimately want to do on
  reload — log it, emit a metric, invalidate a derived cache, warm something.
- The holder shape carries one well-known trap: callers fetch once at startup, hold the
  reference forever, and never see a change. This is the documented Commons Configuration
  failure mode, and it will happen here too.

## Decision

`registry.get(name)` is the primary API and always returns the current bundle.
`onReload` and `onReloadFailure` are optional secondary hooks.

The don't-cache contract is documented prominently — in the README and in the Javadoc of
`get` itself, where someone about to make the mistake will actually see it.

## Consequences

- Both real usage patterns are served, with the safe one as the default path.
- A small additional API surface for the listeners, justified by use cases the holder
  cannot serve.
- The caching trap is mitigated by documentation only, which is imperfect. The transparent
  fix — a wrapper that follows reloads behind a stable reference — is deferred to v2
  (ADR-0011).
- The listener *shape* is not settled here: ADR-0012 makes it snapshot-level with a change
  object rather than per-bundle.
