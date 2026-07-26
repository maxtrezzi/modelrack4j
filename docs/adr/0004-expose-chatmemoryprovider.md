# ADR-0004: Expose `ChatMemoryProvider`, not a bare `ChatMemory`

- **Status:** Accepted
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

The bundle includes memory (ADR-0003). LangChain4j offers two shapes for it: a
`ChatMemory`, which holds one conversation, and a `ChatMemoryProvider`, which maps a
memory id to a memory. Choosing the wrong one is quietly catastrophic in a multi-user
deployment.

## Forces

- One `ChatMemory` instance is one conversation's history. Handing a single shared
  instance to a multi-user application means every user's turns land in the same buffer —
  a correctness and privacy failure, not a performance one.
- Real applications are multi-user by default, and `AiServices` already accepts a
  `ChatMemoryProvider` precisely for that case.
- Deriving a single-conversation memory from a provider is trivial; going the other way
  is not.
- Memory is provider-**independent**: the ecosystem has no `<Provider>ChatMemory`. It sits
  in the bundle for assembly convenience, not because it depends on the chat model.
- One genuine coupling exists nonetheless. Token-window memory needs a
  `TokenCountEstimator`, and estimation is provider-specific.

## Decision

The bundle exposes `Optional<ChatMemoryProvider>`. Memory is constructed in the core
module, from the `memory` configuration block, without provider involvement — except the
`TokenCountEstimator` required by token-window memory, which is requested from the
provider factory (ADR-0005).

## Consequences

- Multi-user applications are correct out of the box, which is the case most likely to be
  got wrong by hand.
- Single-conversation use costs one extra call to obtain a memory from the provider.
- Memory construction stays in core, so provider modules do not each reimplement it. The
  one real provider dependency is isolated at the single point where it actually exists.
- Configuring token-window memory against a provider with no estimator is a validation
  failure, caught at load time by the provider's `validate()` rather than at first use.
