# ADR-0031: A rejected reload is logged whether or not anyone is listening

- **Status:** Accepted
- **Date:** 2026-08-23
- **Supersedes:** —
- **Amends:** ADR-0029 (adds an unconditional log to the failure path it defined)

## Context

Found while writing the README at M5, not while building M3: with `watch(true)` and no
`onReloadFailure` listener registered, a configuration file that fails to parse or validate
produces **no output of any kind**. The reload is correctly rejected, the previous snapshot
stays live — and the user, who has just saved a file, sees nothing happen and has nothing to
look at.

The failure mode this creates is worse than it first sounds, because it is silent *and*
sticky. Every subsequent edit to that file is rejected too, for the same reason, so a typo
does not delay one reload — it disables reloading until someone notices the models are stale
and goes looking. The one thing that would explain it is the exception, which at that point
has been dropped.

[ADR-0029](0029-reload-callbacks-are-quiet-contained-and-not-a-heartbeat.md) settled what
callbacks mean; it did not say what happens when nobody registered one.
[ADR-0028](0028-core-logs-through-slf4j-api.md) already established the mechanism: the
watcher thread produces outcomes that have no caller to throw at, which is why core declares
`slf4j-api` at all. A rejected reload is exactly that kind of outcome and was the one case
not using it.

## Forces

- **The library must not be the only thing that knows.** An error the application cannot
  discover is, from the application's side, indistinguishable from a bug in the library.
- **Against a log: the application may already handle it.** A listener that logs the failure
  itself now produces two lines for one event. Real, and minor — the level and the logger
  name are both configurable, which is what logging frameworks are for.
- **Against a *conditional* log ("log only when no listener is registered"): the diagnostic
  disappears exactly when it is needed most.** A listener that is registered but throws, or
  swallows, or is registered after the first failure, would leave the failure unrecorded —
  and the behaviour would then depend on registration order, which nothing documents.
- **Throwing is not available.** The reload runs on the watcher thread. There is no caller,
  and propagating would kill the thread, converting a rejected reload into no more reloads
  at all.
- **WARN, not ERROR.** Nothing is broken in the running application: it continues on a valid
  configuration. What is broken is the file the user just edited.

## Decision

`LlmRegistry.reload()` logs every rejected reload at **WARN**, with the cause attached,
before notifying failure listeners. The log is unconditional — it does not consult whether
any listener is registered.

Failure listeners keep their existing contract and remain the mechanism for doing anything
beyond logging: alerting, exposing a health signal, failing a readiness probe. The Javadoc
on `onReloadFailure` says so, and the README documents the logger name so the line can be
silenced deliberately rather than by accident.

Successful reloads stay silent at WARN and INFO. ADR-0029's rule that a callback means
something changed is untouched, and this ADR is not a licence to narrate ordinary operation.

## Consequences

- A broken configuration file is discoverable from the logs alone, with no code written and
  no listener registered — which is the state every application is in on day one.
- Applications that log failures themselves see the event twice until they silence
  `io.github.maxtrezzi.modelrack4j.LlmRegistry` or raise its level. Accepted: a duplicate
  line costs a configuration entry, a missing line costs an afternoon.
- Core's dependency on a logging API is now load-bearing in a second place, which further
  entrenches ADR-0028. Consumers still supply their own binding; with none on the classpath
  SLF4J prints its no-provider notice and the warning is lost — that is the consumer's
  choice, and the README states it.
- **Do not make this conditional on listener registration.** It reads like an obvious
  courtesy to applications that handle the event, and it silently removes the diagnostic
  from the cases where the listener is the thing that is wrong.
