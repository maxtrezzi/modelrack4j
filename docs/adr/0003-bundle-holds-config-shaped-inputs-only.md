# ADR-0003: A bundle holds the config-shaped inputs only

- **Status:** Accepted
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

The library hands the caller a "bundle" of ready-built LangChain4j objects for a named
configuration. Which objects belong in it needs a boundary that holds up under pressure,
because the intuitive formulation — "everything that depends on the `ChatModel`" — has no
natural edge and would grow without limit.

## Forces

- `AiServices.builder()` accepts a mixed set of inputs, and they divide cleanly on one
  criterion: whether a configuration file can express them at all.
- One group is **config-shaped**: chat model, streaming chat model, moderation model,
  memory. These are fully described by names, numbers, and flags.
- The other is **code-shaped**: the AiService interface itself, `@Tool` methods, RAG
  retrievers, guardrails. These are types and behaviour. No config file can express a
  method body, and no amount of schema design changes that.
- The split is therefore not a preference. It is a hard limit of static configuration that
  happens to fall along a line LangChain4j itself already draws.

## Decision

The bundle contains exactly the config-shaped set: `ChatModel` (always), plus optional
`StreamingChatModel`, `ModerationModel`, `ChatMemoryProvider`, and `TokenCountEstimator`
where the provider supports it.

Code-shaped inputs are permanently out of scope — not deferred, not "maybe in v2".

## Consequences

- The boundary is LangChain4j's own, which makes it explicable in the README rather than
  arbitrary.
- Scope-creep requests ("add tool support", "wire up my retriever") get a principled no,
  with a reason that does not depend on maintainer bandwidth.
- Callers still assemble their own `AiServices` — the library supplies the parts that a
  file can describe and stops there. That assembly step must be shown in the quick start
  so the division of labour is obvious.
- `EmbeddingModel` sits outside the v1 bundle for a different reason: it is config-shaped
  but does not depend on the chat model, so it does not belong to this cohesion group yet.
