# ADR-0021: Token estimation is universal; the capability that varies is its cost

- **Status:** Accepted — the universality premise amended by [ADR-0022](0022-glm-via-the-community-module-and-its-bom.md)
- **Date:** 2026-08-03
- **Supersedes:** —
- **Amends:** [ADR-0004](0004-expose-chatmemoryprovider.md) and [ADR-0010](0010-discriminators-only-with-two-real-variants.md) — the premise that a `TokenCountEstimator` may be absent

## Context

[Task 0.6](../tasks/phase-0-verification.md#task-06--provider-capability-matrix) built the
provider × capability matrix against the pinned `1.18.0`
([ADR-0018](0018-manage-langchain4j-versions-via-bom.md)), reading the artifacts rather than
the documentation.

Moderation came out as expected: **only OpenAI** ships a `ModerationModel`.

Token estimation did not. Every provider ships a `TokenCountEstimator` — OpenAI, Anthropic,
and both Gemini modules. The expectation written into
[ADR-0004](0004-expose-chatmemoryprovider.md) ("estimation is provider-specific", token-window
memory "requires a provider offering a `TokenCountEstimator`") and restated in
[ADR-0010](0010-discriminators-only-with-two-real-variants.md) treats availability as the
thing that varies. It is not. Availability is universal.

What varies is **how the count is obtained**, and the difference is structural rather than
incidental:

| Estimator | Constructed from | Mechanism |
|---|---|---|
| `OpenAiTokenCountEstimator` | a model name, nothing else | local, `jtokkit` (a declared compile dependency) |
| `AnthropicTokenCountEstimator` | builder with `apiKey`, `baseUrl`, `timeout`, `httpClientBuilder` | HTTP call to `/v1/…count_tokens` |
| `GoogleAiGeminiTokenCountEstimator` | builder with `apiKey`, `baseUrl`, `timeout`, `httpClientBuilder` | HTTP call to `countTokens` |
| `GoogleGenAiTokenCountEstimator` | builder, same shape | HTTP call |

The constructor signatures are the evidence: one takes a string, the others take
credentials and a timeout. Those are not implementation details of the same thing.

This matters because `TokenWindowChatMemory` calls the estimator on message eviction. On
three of four providers that is a network round trip, billed and rate-limited, in the path
of ordinary conversation turns — and it fails when the network does, in a component the
application thinks of as in-memory bookkeeping.

## Forces

- **A two-valued capability check is now wrong in the dangerous direction.** "Does this
  provider have a `TokenCountEstimator`?" answers yes for all four, so validation modelled
  on availability passes every configuration — including the ones that will make an HTTP
  call per evicted message. The check would exist and catch nothing.
- **Fold cost into the capability, and validation can say something true.** Three values —
  absent, local, remote — is one more than the ecosystem needs today for moderation, but
  exactly what token estimation needs.
- **Against a richer model:** [ADR-0010](0010-discriminators-only-with-two-real-variants.md)
  argues against options that do not earn their place, and a third enum value on the strength
  of one release of one ecosystem is speculative generality if the distinction later
  evaporates.
- But it does not look like it will evaporate. Local counting requires a published,
  reimplementable tokenizer. OpenAI publishes one; Anthropic and Google treat tokenization as
  a server-side concern. The split follows vendor policy, not library maturity.
- **`absent` must stay representable** even though no current provider is absent, because
  the GLM route is unresolved ([D1](../tasks/open-decisions.md#d1--glm-route-if-no-maintained-module-exists))
  and an OpenAI-compatible-endpoint factory would inherit OpenAI's *tokenizer* only by
  accident of shape, not by correctness.

## Decision

The `ProviderFactory` SPI exposes token estimation as a **three-valued** capability —
`ABSENT`, `LOCAL`, `REMOTE` — not a boolean. Moderation stays boolean; it has exactly one
implementation and no cost distinction to draw.

`validate()` uses the distinction rather than merely recording it: configuring
`memory.type = token-window` on a `REMOTE` provider is a condition validation must surface,
because nothing else in the system will. **What it does about it — reject, warn, or require
an explicit opt-in — is [D3](../tasks/open-decisions.md#d3--token-window-memory-on-a-remote-estimator),
left to the owner** because it changes the config schema, which is not this ADR's to change.

The matrix for `1.18.0` is recorded in
[Task 0.6](../tasks/phase-0-verification.md#task-06--provider-capability-matrix) and is the
source the README capability table is generated from. It is a fact about a pinned version,
not a permanent property: **re-verify on every LangChain4j bump**, the same way the
`opennlp` exclusion must be ([ADR-0020](0020-core-depends-on-langchain4j-aggregate.md)).

## Consequences

- Capability-aware validation — the differentiator claimed in
  [ADR-0002](0002-scope-to-langchain4j-llm-configuration.md) — now has something real to
  check for memory. Under the old two-valued model it would have passed everything and
  looked like it was working.
- Moderation validation is unchanged and stays simple: OpenAI yes, everyone else no. A
  config naming `moderation` on Anthropic or Gemini fails fast, as designed.
- The bundle's moderation field being `Optional` is vindicated: for three of four providers
  it is permanently empty.
- Provider factories must report a cost class they cannot derive automatically — it is
  knowledge about the upstream implementation, hand-maintained per factory, and it can go
  stale silently when upstream changes mechanism. Accepted because the alternative is
  validation that cannot distinguish a local tokenizer from a billed API call.
- **Do not collapse this back to `hasTokenCountEstimator()` because all four currently
  return true.** That reading is exactly backwards: the uniformity is what makes the boolean
  useless, and the boolean would then silently bless the configuration this ADR exists to
  flag.
