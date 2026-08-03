# ADR-0005: Abstract Factory per provider, discovered via `ServiceLoader`

- **Status:** Accepted — core's dependency set amended by [ADR-0020](0020-core-depends-on-langchain4j-aggregate.md)
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

Each named configuration names a provider, and the library must turn that into a set of
concrete LangChain4j objects. How provider support is structured determines the
dependency footprint imposed on every consumer and whether the library can validate
configuration before anything is built.

## Forces

- **Providers differ in capabilities, not just parameters.** Moderation is roughly an
  OpenAI-family feature; token estimation varies. A configuration requesting moderation
  from a provider that has none must fail at load time with a clear message, which means
  capability knowledge has to live per provider.
- **Objects within a bundle must be mutually consistent** — same provider, same
  credentials, same base URL. Producing a family of related objects that must agree is
  the textbook Abstract Factory situation.
- **Core must not drag in every provider.** If core depended on each
  `langchain4j-<provider>` module, every consumer would inherit all of them along with
  their transitive HTTP and serialization stacks, to use one.
- Offline testability matters: the core reload, layering, and diffing logic must be
  testable with no network and no API keys.

## Decision

A `ProviderFactory` SPI — `providerId()`, `validate(LlmConfig)`, and creator methods
returning the chat model plus `Optional` streaming, moderation, and token-estimator
objects. One small Maven module per provider, each registering its implementation under
`META-INF/services/` and discovered at runtime through `java.util.ServiceLoader`.

Core depends only on `langchain4j-core` and Typesafe Config (ADR-0007). An unrecognised
provider id fails fast, and the error message lists the ids actually found on the
classpath — not a hard-coded list.

## Consequences

- Consumers pay only for the providers they add to their build.
- Third parties can add a provider by dropping a jar on the classpath, with no change to
  this project.
- A `FakeProviderFactory` in core's test scope makes the entire core testable offline,
  which is what allows the reload and atomicity tests to be fast and deterministic.
- Capability validation has a natural home, and each factory owns the facts about its own
  provider rather than core maintaining a matrix it cannot keep current.
- Cost: multi-module build complexity, plus a released SPI that becomes a compatibility
  surface. Accepted deliberately — the `Optional`-returning shape means new capabilities
  can be added without breaking existing implementations.
