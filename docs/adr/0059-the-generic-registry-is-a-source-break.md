# ADR-0059: Accept that a generic registry breaks source compatibility

- **Status:** Accepted
- **Date:** 2026-09-07
- **Supersedes:** —
- **Amends:** [ADR-0055](0055-custom-properties-are-carried-as-text.md), whose compatibility
  consequence was wrong

## Context

ADR-0055 gave `LlmRegistry`, `LlmBundle` and `LlmSnapshot` a type parameter, so that a
registered `CustomPropertiesHandler<T>` fixes what `customProperties()` returns without a class
token. Its Consequences said this was safe for existing callers:

> Existing callers keep compiling, since a raw type is legal and generics are erased, so this is
> neither a source nor a binary break.

**That is false**, and the compiler said so the first time the change existed. A raw type erases
*every* generic member of the class, not only the members that mention the parameter, so
`reload()` and `store()` come back as a raw `Optional` rather than as
`Optional<ReloadChange>`. Measured against code written for `0.1.0`:

| the shape in existing code | result |
|---|---|
| `Optional<ReloadChange> c = registry.reload();` | compiles, with an unchecked warning |
| `registry.reload().orElseThrow().updated()` | **`cannot find symbol`** — the raw `Optional` yields `Object` |

So a caller who declares the target type is unaffected, and one who chains through the result is
not. Binary compatibility is untouched, because generics are erased at run time: a jar compiled
against `0.1.0` keeps linking.

The claim was made in three places at once — the ADR, the decision entry and the task — and
none of them was somewhere it could be checked. This ADR exists because the decision was taken
on it.

## Forces

**Reverting the generic would restore compatibility.** A class token at each read —
`customProperties(SupportProps.class)` — leaves every existing signature alone. It also moves a
compile-time check to run time for the life of the API, and it was rejected on that ground while
the compatibility cost was believed to be zero. Now that the cost is real, the comparison is a
genuine one rather than a formality.

**But the break is narrow and its fix is mechanical.** What breaks is the raw-type call chain,
in a library whose registry is normally held in one field and used through `get()`. The
correction is to write `LlmRegistry<Void>`, or `var` for a local. Nothing about a caller's
configuration, provider or model code changes.

**The project is `0.x` and the CHANGELOG reserves the right to break in a minor.** That is not
a licence to break carelessly, but it is the difference between a cost to state and a cost to
avoid at any price.

**A published version is permanent.** `0.1.0` is on Central and cannot be changed. Choosing the
class token now to protect a source shape means living with the token in every release
afterwards; choosing the parameter means one migration note, once, while the library has one
consumer.

## Decision

**The type parameter stays, and the break is stated rather than denied.**

- The CHANGELOG carries it under **Changed**, with both shapes above and the correction to make:
  write `LlmRegistry<Void>`, or `var`.
- ADR-0055's decision is unaffected. Only its compatibility consequence is replaced by the table
  above, and its Status now says so.
- No deprecation cycle and no compatibility overload. A `customProperties(Class<T>)` kept beside
  the parameter would be the class token this shape exists to remove, carried for the life of
  the API to soften a one-line edit.

## Consequences

**An upgrading application may have to change a declaration.** It will be told by the compiler,
at the call site, with the type it needs — which is the best moment and place for this class of
break.

**The library's own examples show the shape without the noise.** A parameter that never uses the
type takes `LlmBundle<?>`, and a local takes `var`, so the five examples contain no `Void` at
all. An application that passes a registry through its own method signatures will write
`LlmRegistry<Void>` there; the examples cannot hide that, and the CHANGELOG says it.

**A compatibility claim about erasure is not obvious enough to assert.** This one was written
three times from general knowledge and was wrong in a way a single compilation exposed. Where a
future decision turns on what still compiles, compile it.
