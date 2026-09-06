# ADR-0058: Null does not remove a configuration

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

Layering adds configurations and overrides parts of them. It does not remove them, and the way
it says so is poor. Measured on 2026-09-06 against a base defining `SL` and `SH`:

| in a higher layer | today |
|---|---|
| `llm.SH = null` | `ConfigValidationException: llm.SH must be a configuration block, but is of type NULL` |
| `llm = null` | no `llm` block found |
| `llm = {}` | clears nothing: HOCON merges objects, `[SH, SL]` survive |

That first message describes an internal value type. It is also reached by accident: the check
exists for `llm.SL = "a string"`, a real mistake, and the removal idiom falls into it.

A reader will try that idiom, because this project already uses it one level down. ADR-0032
clears `description` from a higher layer with `description = null`. Nothing tells them the same
syntax does not extend to a whole configuration.

## Forces

**There is a case for supporting removal, and it is not empty.** In this repository's five
examples, no name is written as a literal at a call site: `ThreeModelCouncil` polls whatever
`names()` reports, `ConsoleChat` builds its menu from it, `DatabaseSource` iterates it. For an
application shaped that way, removing a configuration in a layer is a change to configuration
alone.

**Against it, and decisive: nothing needs it.** Whoever wants a configuration gone can remove it
from the layer that defines it. Layers exist to override values across environments, not to
compose the set of configurations that exist. Where a name *is* written at a call site, removing
it means changing code, and changing code means the base file can change too. Supporting removal
would add a second way to do something the first way already does, which is decoration rather
than capability.

**What is genuinely wrong today is the message, and that is worth fixing on its own.** A refusal
that names an internal type teaches nothing; a refusal that says removal is not supported ends
the question.

**One asymmetry has to be closed rather than inherited.** `llm = null` empties the root, and
[ADR-0057](0057-an-empty-configuration-is-valid.md) makes an empty result legal, so without a
deliberate answer it would become a working back door to the operation this decision refuses.
Measured that the two cases are distinguishable: with `llm = null` the root contains `llm` with
value type NULL, while a genuinely absent block is not in the root at all.

## Decision

**Null never removes a configuration.** Both forms are refused, each naming the layer and line
that wrote the null:

- `llm.<name> = null` — the message says that a configuration cannot be removed from a higher
  layer, that null clears a value inside a block rather than the block itself, and that the way
  to remove one is to remove it from the layer that defines it.
- `llm = null` — refused in the same terms. An `llm` key that is simply **not there** stays
  legal and produces an empty registry, which is ADR-0057 and is untouched: absence is not
  removal.

Every other non-object value keeps the refusal it has today, with its type name and its origin.
`llm = {}` continues to clear nothing, because HOCON merges objects — which is also what makes
an empty layer harmless.

## Consequences

**Layering stays add-and-override**, and the set of configurations is decided by the layer that
defines them. That is one rule, not two.

**A reader who tries the ADR-0032 idiom one level up is told why it does not work**, and where to
go instead, rather than reading about a value type.

**ADR-0057 is independent of this and stays so.** An empty registry is reached by defining
nothing, not by nulling something. The two were drafted as halves of one change; they are not,
and the earlier framing overstated it.

**Do not later "simplify" this into a skip.** Making a NULL value silently absent turns a refusal
into a removal, which is the decision this ADR declined — and it would do it quietly, making a
name disappear for a reason written in a layer the reader may not have open.
