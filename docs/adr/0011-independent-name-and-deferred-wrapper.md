# ADR-0011: Independent name; the hot-swap wrapper deferred to v2

- **Status:** Accepted
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

Two questions with a shared root: how closely to associate with LangChain4j in name and in
API surface, and how much to build before calling v1 finished.

## Forces

- The `langchain4j-` artifact prefix implies official status this project does not have.
  Maintainers reasonably guard it, and adopters would reasonably be misled.
- The `4j` **suffix** carries no such implication — it is generic Java convention
  (log4j, neo4j, resilience4j) and denotes the language, not an affiliation.
- LangChain4j's API surface has churned: `ChatLanguageModel` became `ChatModel`, and
  request parameters were redesigned. The smaller the depended-on surface, the less each
  upstream change costs.
- The registry (holder plus reload) and the convenience wrapper are separable. The
  registry is the mechanism; the wrapper is sugar over it.
- A v1 that must ship both is a v1 that ships later, and the wrapper's design benefits
  from the registry existing first.

## Decision

Use the independent name `modelrack4j`, checked for collisions, with modules
`modelrack4j-core` and `modelrack4j-provider-*` under an `io.github.<owner>` groupId.
State the unofficial, independent relationship plainly in the README.

Keep the public API touching only `langchain4j-core` types.

Defer `ReloadableChatModel` — a `ChatModel` implementation backed by an `AtomicReference`
fed by the registry — to v2. Design for it; do not build it.

## Consequences

- Honest positioning, with no implied endorsement.
- Exposure to upstream churn is minimised, and concentrated in the provider modules where
  it is unavoidable.
- v1 stays small enough to finish, with a natural v2 already scoped.
- Until the wrapper exists, callers must re-fetch through `registry.get(name)` rather than
  holding one long-lived reference — which is why the don't-cache warning in ADR-0009 has
  to be prominent.
- **Do not misread this as deferring reload.** Hot *reload* — watching files and rebuilding
  bundles — is in v1. What is deferred is only the wrapper that lets a single long-lived
  `ChatModel` reference follow reloads transparently. v1 ships the mechanism; v2 ships the
  sugar.
