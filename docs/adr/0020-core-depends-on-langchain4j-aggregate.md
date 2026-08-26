# ADR-0020: Core also depends on the `langchain4j` aggregate, for `ChatMemoryProvider`

- **Status:** Accepted — dependency set amended by [ADR-0028](0028-core-logs-through-slf4j-api.md)
- **Date:** 2026-08-03
- **Supersedes:** —
- **Amends:** [ADR-0005](0005-provider-factory-spi-via-serviceloader.md) — the core dependency set only

## Context

[ADR-0005](0005-provider-factory-spi-via-serviceloader.md) fixes `modelrack4j-core`'s
dependencies at exactly two: `langchain4j-core` and `com.typesafe:config`.
[ADR-0004](0004-expose-chatmemoryprovider.md) has the bundle expose
`Optional<ChatMemoryProvider>`, built in core because memory construction is
provider-independent.

[Task 0.5](../tasks/phase-0-verification.md#task-05--confirm-interface-names) checked those
type names against the pinned `1.18.0` ([ADR-0018](0018-manage-langchain4j-versions-via-bom.md))
and found the two decisions cannot both hold as written. **`ChatMemoryProvider` is not in
`langchain4j-core`.** It lives in the `dev.langchain4j:langchain4j` aggregate artifact, at
`dev.langchain4j.memory.chat.ChatMemoryProvider`, together with `MessageWindowChatMemory`
and `TokenWindowChatMemory` — the two implementations the config schema names.

What *is* in `langchain4j-core`: `ChatModel`, `StreamingChatModel`, `ModerationModel`,
`TokenCountEstimator`, `ChatMemory`, `ChatMemoryStore`. So the split does not follow
"interfaces in core, implementations elsewhere" — `ChatMemoryProvider` is itself a
functional interface, and it sits outside core regardless.

This was assumed, never verified; the plan predates the pinned artifact.

## Forces

- **ADR-0004's rationale is interoperability**, not convenience. The bundle exposes
  LangChain4j's own `ChatMemoryProvider` so it can be handed straight to `AiServices`.
  Substituting a modelrack4j-defined equivalent would keep the dependency list short and
  destroy the only reason the type is exposed at all.
- **The marginal dependency cost is near zero.** The aggregate's own dependencies are
  `langchain4j-core`, Jackson, SLF4J — all already present transitively — plus
  `opennlp-tools`, which is referenced by exactly one class in the jar,
  `DocumentBySentenceSplitter`. That is RAG document splitting, permanently out of scope
  ([ADR-0003](0003-bundle-holds-config-shaped-inputs-only.md)), so excluding it is safe and
  verified rather than hoped.
- **The consumer almost certainly has the aggregate already.** The application that wants
  `ChatMemoryProvider` wants it *for* `AiServices`, which is in the same artifact. Core is
  not dragging a stranger onto the classpath.
- **Against:** ADR-0005's "exactly two dependencies" is a bright line, and bright lines are
  valuable precisely because they are not negotiated case by case. Three is not two, and the
  aggregate carries `dev.langchain4j.service` (AiServices, guardrails) — things this project
  deliberately does not build on.
- **A separate `modelrack4j-memory` module** would preserve ADR-0005 verbatim, but splits a
  bundle across two modules so that one field of it comes from elsewhere, for a dependency
  the consumer already has. The bright line would be intact and the design worse.

## Decision

`modelrack4j-core` depends on **three** artifacts:

| Artifact | For |
|---|---|
| `dev.langchain4j:langchain4j-core` | `ChatModel`, `StreamingChatModel`, `ModerationModel`, `TokenCountEstimator`, `ChatMemory` |
| `dev.langchain4j:langchain4j` | `ChatMemoryProvider`, `MessageWindowChatMemory`, `TokenWindowChatMemory` |
| `com.typesafe:config` | HOCON parsing ([ADR-0007](0007-layered-hocon-via-typesafe-config.md)) |

Exclude `org.apache.opennlp:opennlp-tools` from the `langchain4j` dependency. It serves only
out-of-scope document splitting.

Everything else ADR-0005 decides is unchanged: no provider artifact may appear in core, the
`ProviderFactory` SPI and its `ServiceLoader` discovery are untouched, and each provider
still lives in its own module. **This ADR widens the dependency list by one named artifact
for one named type — it is not a general licence to add LangChain4j artifacts to core.**

Core still must not reference `dev.langchain4j.service` (`AiServices`, guardrails) even
though the aggregate now puts those classes on the classpath. That boundary is
[ADR-0003](0003-bundle-holds-config-shaped-inputs-only.md)'s, and it survives intact.

## Consequences

- `ChatMemoryProvider` stays LangChain4j's own type, so a bundle's memory still drops
  straight into `AiServices` — the guarantee ADR-0004 exists to provide.
- Core's dependency list is now a rule with an exception in it, which is weaker than a
  bright line. The mitigation is that the exception is named, argued, and scoped to one
  type; a future addition needs its own ADR rather than pointing at this one as precedent.
- `AiServices` and guardrails become classpath-visible to consumers of core. They remain out
  of scope for this library, and being reachable is not the same as being supported.
- The `opennlp-tools` exclusion must be re-verified on LangChain4j upgrades. If a future
  release makes the memory classes depend on it, the exclusion turns a working build into a
  `NoClassDefFoundError` at runtime — dependency convergence in M0 will not catch that,
  because an exclusion is a deliberate absence rather than a conflict.
- **Do not "tidy" this by dropping back to `langchain4j-core` alone.** It compiles right up
  until `ChatMemoryProvider` is imported, and the failure looks like a missing artifact
  rather than a reversed decision.
