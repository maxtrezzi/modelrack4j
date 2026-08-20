# ADR-0023: Take Gemini from `langchain4j-google-ai-gemini`, the stable module

- **Status:** Accepted
- **Date:** 2026-08-20
- **Supersedes:** —
- **Amends:** —

## Context

[Task 0.4](../tasks/phase-0-verification.md#task-04--which-gemini-module) asked which of two
Gemini modules upstream recommends. **There are three**, and the third is what makes the
question confusing:

| Module | Line | Underlying transport | Docs status |
|---|---|---|---|
| `langchain4j-google-ai-gemini` | **stable**, `1.19.0` | its own HTTP client on `langchain4j-http-client-jdk` — **no Google SDK** | documented as the primary Gemini integration; no deprecation notice |
| `langchain4j-google-genai` | beta, `1.19.0-beta29` | `com.google.genai:google-genai:1.63.0`, Google's official SDK | *"This integration is currently marked as **Experimental**. The API and implementation are subject to change in future releases."* |
| `langchain4j-vertex-ai-gemini` | beta, `1.19.0-beta29` | the Vertex AI SDK | no deprecation notice on its page |

The search-engine answer to "which Gemini module does LangChain4j recommend" is *migrate to
`-google-genai`*, and that answer is real but **about a different module**. Google announced
the Vertex AI SDK would stop being supported after June 2026; upstream issue
langchain4j/langchain4j#4383 ("Migrate from Vertex AI SDK to Google Gen AI SDK before June
2026 deprecation") proposed a new module on the Gen AI SDK and *"gradually deprecate Vertex
AI–specific bindings"*. It was closed 2026-05-15, and `-google-genai` is that new module.

**That pressure does not reach `-google-ai-gemini`, because it does not use a Google SDK at
all.** Its compile dependencies are `langchain4j-core`, `langchain4j-http-client[-jdk]` and
Jackson: it speaks the Gemini Developer API over REST directly. The SDK deprecation is
`-vertex-ai-gemini`'s problem, and `-google-genai` is its answer.

The two modules also target different APIs. `-google-ai-gemini` is the Gemini Developer API,
authenticated with an API key. `-google-genai` wraps a unified SDK covering both that API and
Vertex AI, where Vertex needs Google Cloud credentials and a project — not the API-key-shaped
configuration this library exists to express
([ADR-0002](0002-scope-to-langchain4j-llm-configuration.md)).

## Forces

- **[ADR-0018](0018-manage-langchain4j-versions-via-bom.md) settles most of this already:**
  track the stable line, and take a beta-line artifact only where no stable equivalent
  exists. Here a stable equivalent does exist, so the rule points at
  `-google-ai-gemini` without further argument. This is the mirror image of GLM
  ([ADR-0022](0022-glm-via-the-community-module-and-its-bom.md)), where beta was taken
  because nothing stable existed.
- **The newer module is genuinely the strategic direction**, and that pulls the other way:
  Google is consolidating on the Gen AI SDK, new capabilities land there first, and picking
  the older module means a migration eventually. Against that: "eventually" has no date on
  it, `-google-ai-gemini` is not built on the deprecated SDK, and upstream marks the
  replacement Experimental in its own documentation.
- **Experimental is a poor foundation for a configuration library.** This project's whole
  proposition is that a config file maps to a working bundle and keeps working across
  reloads. A module whose *"API and implementation are subject to change"* puts that promise
  in someone else's hands, and every change surfaces to users as a config-shaped break.
- **`-google-ai-gemini` is not a legacy module on life support.** 60 releases, shipping in
  lockstep with mainline (`1.17.0` 2026-06-26, `1.18.0` 2026-07-17, `1.19.0` 2026-08-14),
  documenting current models including `gemini-3-pro-preview`, with zero `@Deprecated`
  markers in the artifact.
- **Weight.** `-google-genai` pulls in the Google SDK plus `jackson-module-kotlin`, and so
  the Kotlin standard library, into a provider module that needs one chat model, one
  streaming model and a token estimator. `-google-ai-gemini` adds none of that.
- **Capability parity means the choice is reversible.** Both ship `ChatModel`,
  `StreamingChatModel` and a `TokenCountEstimator`, so the
  [ADR-0005](0005-provider-factory-spi-via-serviceloader.md) factory looks the same either
  way. Only the builder calls inside it differ.

## Decision

**Build `modelrack4j-provider-gemini` on `dev.langchain4j:langchain4j-google-ai-gemini`,**
the stable module, at the version managed by `langchain4j-bom`.

Neither `-google-genai` nor `-vertex-ai-gemini` is used in v1. The provider name in config
stays `gemini`, so the module behind it can change without a config-visible break.

**Revisit when either of these becomes true** — the reason this ADR exists rather than a
task note:

1. `-google-genai` drops the Experimental marker and reaches the stable line, or
2. `-google-ai-gemini` gains a deprecation notice or falls off the mainline release cadence.

Neither is true today; both are cheap to check on a version bump.

## Consequences

- The Gemini provider stays on the stable line, so
  [ADR-0018](0018-manage-langchain4j-versions-via-bom.md)'s BOM handles its version and
  nothing beta-shaped enters the build for Gemini.
- **A migration to `-google-genai` is accepted as likely, later.** It is confined to one
  provider module and, because the capability set is identical, is a rewrite of builder calls
  rather than a redesign of the SPI. The config surface — provider name `gemini`, API key,
  model name — does not move.
- **Vertex AI is not reachable in v1.** Anyone needing Gemini through Google Cloud rather
  than an API key is not served, which is consistent with the API-key-shaped configuration
  the library targets, but is a real limitation to state in the README rather than let a user
  discover.
- **Do not "upgrade" the Gemini provider to `-google-genai` because it is newer.** Newer here
  means Experimental, one release line down, and a Kotlin standard library in the dependency
  tree. The two revisit triggers above are the conditions for changing this — not novelty.
- **Do not read the Vertex AI SDK deprecation as applying to this module.** It is the single
  most likely misreading of this decision, it is what a web search returns for "which
  LangChain4j Gemini module", and `-google-ai-gemini` has no Google SDK in it at all.
