# ADR-0002: Scope to LangChain4j LLM configuration, not generic reloadable config

- **Status:** Accepted
- **Date:** 2026-07-26 *(record backfilled; the decision itself predates it)*
- **Supersedes:** —

## Context

The library's core mechanism — watch configuration files, rebuild objects when they
change — is not specific to language models. The same machinery could serve any typed
configuration. That generality is a trap worth naming explicitly before any code is
written, because it determines what the library competes with.

## Forces

- Generic file-reloading configuration is already owned territory. Apache Commons
  Configuration ships `ReloadingFileBasedConfigurationBuilder` with
  `PeriodicReloadingTrigger`, is mature, and is widely deployed. A new entrant competing
  on generality loses by default.
- That incumbent has two real weaknesses: reload detection is polling-based, and access is
  stringly-typed. Neither is fatal in the generic case, and neither is worth a new library
  on its own.
- What a generic library structurally *cannot* do is know that moderation is unavailable
  on one provider, or that token-window memory needs a token estimator the provider may
  not offer. Capability validation requires domain knowledge.
- There is visible upstream interest in dynamic model configuration, but it should not be
  overstated: the referenced LangChain4j issues are a question about runtime model
  switching (#1884) and a request for a provider pool with fallback (#874) — the latter
  being something this library explicitly refuses to build. They show activity in the
  area, not demand for this exact feature set. The honest justification is that the
  owner's own application needs it.

## Decision

Scope is LLM configuration for LangChain4j, and nothing wider. The differentiators are
domain-specific by design: push-based `WatchService` detection rather than polling, typed
and validated configuration records rather than string lookups, and provider-capability
awareness that no generic library could have.

Position and document the library accordingly — never as a general configuration
solution.

## Consequences

- A smaller audience, in exchange for a defensible one. Competing head-on with Commons
  Configuration is off the table, which is the point.
- Feature requests that would generalise the library ("make it work for any config type")
  have a principled answer rather than an ad-hoc one.
- Generalising later remains possible; the reverse — narrowing after having promised
  generality — is not. The asymmetry is why this is decided up front.
- The "real-time detection" claim is load-bearing for this positioning, which makes the
  macOS polling caveat in ADR-0013 something to document honestly rather than gloss.
