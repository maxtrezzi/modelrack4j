# ADR-0058: A higher layer can remove a configuration

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

Layering can add a configuration and override parts of one. It cannot remove one. Measured on
2026-09-06, with a base layer defining `SL` and `SH`:

| in a higher layer | result |
|---|---|
| `llm.SH = null` | `ConfigValidationException: llm.SH must be a configuration block, but is of type NULL` |
| `llm.SL = null` and `llm.SH = null` | the same, on the first one reached |
| `llm = null` | no `llm` block at all — an empty registry under ADR-0057 |
| `llm = {}` | nothing is cleared: `[SH, SL]` survive, because HOCON merges objects |

`= null` is HOCON's way of removing a key when layers merge, and this project already relies on
it: ADR-0032 uses it to clear `description` from a higher layer. It works for a key inside a
block and fails for a block.

The cause is in `SnapshotLoader.load`. The loop walks `root.keySet()`, which **includes** a key
cleared with `= null` — measured separately, where `entrySet()` excludes it and `hasPath` answers
false — and then rejects the value for not being a `ConfigObject`. The refusal is aimed at
`llm.SL = "a string"`, which is a genuine mistake, and it catches the removal idiom by accident.

## Forces

**The asymmetry is the argument.** A base file that defines `SL`, `SH` and `CR` and an
environment layer that needs `CR` gone is an ordinary layering problem, and the only answers
today are to edit the base file — which defeats layering — or to leave a configuration built and
unused, holding an HTTP client.

**The idiom is already in the project and already documented**, for a key rather than a block.
Two rules for the same syntax at two depths is harder to explain than one.

**Against it: a removal is quieter than an addition.** A `= null` in a layer the reader is not
looking at makes a name disappear, and `get()` then throws for a name the base file plainly
defines. That is the cost, and it is the same cost ADR-0032 already accepted for a key.

**The refusal being repaired must not be weakened.** `llm.SL = "a string"` and `llm.SL = 42` are
mistakes and must stay errors, naming the type and the origin. Only the NULL case changes.

## Decision

A name whose value is NULL in the merged configuration is **absent**, not invalid.

`SnapshotLoader.load` skips it before the type check. Every other non-object value keeps the
refusal it has today, with its message, its type name and its origin.

A layer may therefore remove any configuration, including the last one, which
[ADR-0057](0057-an-empty-configuration-is-valid.md) makes a valid result rather than an error.
The two decisions are taken together because either alone is half a feature: without this one
there is no way to reach an empty result from a higher layer, and without ADR-0057 clearing the
last configuration would fail anyway.

`llm = {}` is not a way to clear anything and is not made into one. HOCON merges objects, so an
empty object contributes nothing — which is also what makes an empty *layer* harmless.

## Consequences

**Layering becomes symmetric.** What a lower layer defines, a higher one can override or remove,
with the same syntax at both depths.

**A name can be absent for a reason that is not in the file the reader has open.** This is the
accepted cost. `get()` on it throws `UnknownConfigurationException` exactly as for a name that
was never defined, and `ReloadChange.removed()` reports it when a reload does the clearing.

**Do not reach for `entrySet()` to implement this.** It excludes NULL entries, which looks like
the same fix and is not: it also flattens nested objects into dotted leaf paths, so the loop
would stop iterating configurations and start iterating their keys.
