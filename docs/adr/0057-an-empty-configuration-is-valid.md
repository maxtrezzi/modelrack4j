# ADR-0057: A registry with no configurations is valid

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

`SnapshotLoader.load` refuses two things that are really one: a merged configuration with no
`llm` block at all, and an `llm` block that defines no names. Each has its own message —
*"No 'llm' block found in any configuration layer"* and *"The 'llm' block is empty: no
configurations to build"*.

No ADR asks for either. They are assumptions written into the loader, and they contradict a
decision that was taken: ADR-0014 says removed names are honoured, the name disappears from the
registry, and a later `get()` throws `UnknownConfigurationException`. Measured on 2026-09-06,
that promise stops holding at the last one:

```
start            : [SH, SL]
after removing SH: [SL]          — honoured
after removing SL: ConfigValidationException: The 'llm' block is empty
names afterwards : [SL]
```

The file says there are no configurations and the registry goes on serving one, indefinitely,
until something is added back. That is the state a reload exists to prevent.

The empty layer is a separate and already-working case: a file that is empty or holds only
comments, sitting among other layers, contributes nothing and the merge takes the rest. Measured
working in any position. What is at issue here is only the case where **nothing anywhere**
defines a configuration.

## Forces

**Fail-fast argues for keeping the refusal.** An empty result usually means the caller pointed
at the wrong file, and a registry with no configurations cannot serve anyone; letting `build()`
succeed moves the failure to the first `get()`, later and further from the cause.

**But the commonest form of that mistake is still caught, by a different mechanism.** A path
that does not exist is a `ConfigException.IO` and becomes `ConfigAccessException` (ADR-0053).
Pointing at the wrong file usually means pointing at no file. What the refusal adds beyond that
is the narrow case of a real, readable, empty file.

**Hot reload makes an empty start a legitimate state, and that is specific to this library.**
A configuration that arrives after the process does — a Kubernetes ConfigMap not yet populated,
a file a deployment writes a moment later — is exactly what the watcher exists for (ADR-0013,
ADR-0024). Refusing to start is refusing the sequence the feature was built to support.

**And an empty configuration is the application's problem, not the library's.** Whether zero
configurations is an error depends entirely on what the application does with them, which the
library cannot know. An application that requires `SL` finds out by asking for `SL`.

## Decision

A configuration that defines no names is valid. Both refusals are removed.

- `build()` succeeds with zero configurations. `names()` returns empty, and `get(anything)`
  throws `UnknownConfigurationException` — the behaviour ADR-0014 already specifies for a name
  that is not there.
- `reload()` may remove the last configuration. The swap happens, and the change object reports
  every name in `removed`.
- A missing `llm` block and an `llm` block defining no names are the same case and produce the
  same result. Neither is distinguished from the other, because neither is an error.

**This does not relax the layer rule.** A registry built with no configuration sources at all
still throws: `ConfigLoader.load` requires at least one layer, and having nowhere to read from is
different from reading and finding nothing.

## Consequences

**The registry can now always be made to agree with the files.** Removing the last configuration
takes effect, so no state exists in which the live registry serves what no layer defines.

**An application that requires a configuration to exist has to say so.** Nothing in the library
will now fail on its behalf at startup. The check is one line — `names()` or a `get()` in a
health check — and it belongs to the application because only the application knows which names
it needs.

**A readable but empty configuration file starts cleanly.** That is the intended gain and it is
also the loss: a caller who points at a real file that happens to be empty gets a working
registry that serves nothing, rather than a message. A caller who points at a path that does not
exist still gets `ConfigAccessException`.

**Do not restore either check as a convenience.** The second one in particular looks harmless and
is not: it makes the last removal unperformable, which is the inconsistency with ADR-0014 that
this decision exists to remove.
