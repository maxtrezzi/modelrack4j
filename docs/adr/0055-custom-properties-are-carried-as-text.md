# ADR-0055: Carry a configuration block's custom properties as text

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

A named configuration block describes one connection to one model. Applications have values
that belong to that connection but that this library has no business understanding: which
prompt template to use, how many times to retry, when to escalate. Today those values can be
written inside a block and the library ignores them in silence — `LlmConfig.fromBlock` reads
only the paths it knows and nothing enumerates the rest — so the file already accepts them and
nothing gives them back.

What is missing is not storage. An application can already keep its own configuration file.
What is missing is **atomicity**: a value inside the block is parsed, validated, published and
rejected in the same swap as the model it belongs to, so a reload cannot leave a new prompt
template beside an old model. That is the only thing this library can offer here that an
application cannot arrange for itself.

The constraints that were fixed before the decision: `LlmConfig` is an immutable record whose
equality *is* the per-name reload diff (ADR-0006); a reload that fails anywhere swaps nothing
(ADR-0012); the core declares four compile dependencies and gains none here (ADR-0005); and
this library is not a general reloadable-configuration solution (ADR-0002).

## Forces

**A typed value on the record is the obvious shape and it was the recommendation twice.** Both
times it was a container the library would read on the application's behalf: a map of strings,
whose accessors are `parseInt` at every call site; a map of `Object`, where `Integer` silently
becomes `Long` past a boundary invisible in the file; or a wrapper delegating to Typesafe
Config's own accessors, which are tolerant about form and strict about content and name the
line, and which was the strongest of the three.

They all fail the same test. A library that supplies typed accessors for values it does not own
carries **two reading mechanisms in one object** — manual reading for its own schema, and a
second mechanism for the application's. The second one has to answer questions the library has
no basis for answering: whether a nested object is allowed, what a list means, which key is a
secret.

**Automatic binding of the library's own schema was considered and does not apply.**
`ConfigBeanFactory` is already on the classpath, maps kebab-case onto camelCase, and converts
durations and lists — but it requires a JavaBean and refuses records outright, and `LlmConfig`
cannot stop being a record without giving up the reload diff. Binding also produces worse
messages than the hand-written parse, which names the block and the key.

**Against carrying text stands the loss of per-value provenance.** Rendering the merged
sub-block back to a string drops the file and line attached to each value, so a failure inside
the application's own parsing cannot point at a line the way the library's own errors do.

## Decision

The optional `custom-properties` sub-block of each named configuration is carried as **text**,
and the library interprets none of it.

1. The merged, resolved sub-block is rendered to text with
   `ConfigRenderOptions.concise()`, which produces JSON. An absent sub-block renders as `{}`.
2. That text is a component of `LlmConfig`, so the per-name reload diff stays a value
   comparison and needs no cooperation from the application.
3. A caller may register **one handler** on the builder, a functional interface whose single
   method takes the text and returns an object, declared `throws Exception` because binding a
   configuration is I/O-shaped in every library a caller is likely to reach for.
4. The handler runs in `SnapshotLoader.buildBundle`, beside `factory.validate` and
   `createChatModel`, and its result is a member of `LlmBundle`. **Parsing is validation:** a
   handler that cannot produce its object throws, and the configuration is rejected.
5. Anything the handler throws is wrapped in `ConfigValidationException`, naming the block and
   keeping the original as the cause.
6. The handler is optional, and it is called even when the sub-block is absent, with `{}`.
7. `LlmConfig.toString()` never prints the text: `{}` when empty and `***` otherwise.

A caller reads `customPropertiesText()` always, and the parsed object through a class token when
a handler was registered. Asking for the object without having registered a handler throws, the
way `get()` on a name that was never configured throws `UnknownConfigurationException`.

## Consequences

**The library gains no opinion.** There is no value type, no key enumeration, no rule about
nested objects, and no separate validator interface — the handler's parse is the validation.
The core interprets nothing, which is further from ADR-0002's rejected position than any typed
shape would have been.

**The application's own binder enforces its own schema, and does it better than this library
could.** A deserializer knows the intended field names; the library could only ever have
refused structure. Jackson refuses an unknown field by default and says which fields it knew.

**The text must stay in `LlmConfig` and the parsed object must stay in `LlmBundle`.** This is
the part to leave alone. Moving the parsed object into the record makes the reload diff depend
on whether the application implemented `equals`; a class without it compares by identity, so
every block looks changed on every reload and every model is rebuilt, with nothing to warn
about it.

**Per-value origins inside the sub-block are given up.** Two things bound the loss: it affects
only the custom properties, since every key the library owns is still read from the merged
`Config` with its origin intact; and the merged sub-block's own origin names every layer that
contributed to it, so a failure can still say which block failed and which layers built it.

**A render and a re-parse happen for each block that changed**, which the carry-over already
limits to blocks actually being rebuilt.

**There is no provider-facing shape.** Every `ProviderFactory` method takes `LlmConfig`, so a
provider reaches the text like any other caller, but it cannot know the application's type. A
provider that parsed the text would be doing the untyped string lookups this library exists to
avoid.

**The handler runs under `reloadLock`**, so it must be fast and must not block or perform I/O —
the contract provider factories already live under. It must not transform the library's own
configuration: a live `LlmConfig` that differs from the file makes the diff meaningless.

**This is not a data channel.** ADR-0032's warning applies unchanged: a field that must not
affect the diff does not belong in `LlmConfig` at all, so this is for small stable values that
belong to *this model configuration*, not to the application at large.
