# ADR-0062: A provider declares whether a block may or must set `api-key` and `base-url`

- **Status:** Accepted
- **Date:** 2026-09-21
- **Supersedes:** —
- **Amends:** —

## Context

Two things a user may reasonably want cannot be configured today.

**A server that is not the vendor's.** Ollama, LocalAI, llama.cpp, vLLM and LM Studio all
serve models from an address the user chooses, and Ollama takes no credential at all. The
schema has no key for an address, so none of them can be reached, including through
`provider = openai`, whose client could reach an OpenAI-compatible server if it were told
where it is.

**A provider with no credential.** `LlmConfig` rejects a blank `api-key` for every block
(`requireText(name, apiKey, "api-key")`). Ollama has none to give: `OllamaChatModel`'s builder
in `langchain4j-ollama` `1.20.0` has no `apiKey` method at all.

What was read from the artifacts, with `javap` on 2026-09-20 and 2026-09-21:

- **Every builder the four modules call that talks to a server takes `baseUrl`.** That is 11
  builders: the chat and streaming chat models of all four providers, `OpenAiModerationModel`,
  and the two remote estimators, `AnthropicTokenCountEstimator` and
  `GoogleAiGeminiTokenCountEstimator`. Gemini's is on the inherited
  `GoogleAiGeminiChatModelBaseBuilder`, not on the subclass. `OpenAiTokenCountEstimator` is
  local and has no address.
- **`langchain4j-ollama` is on the stable line**, `${langchain4j.stable.version}` = `1.20.0`, in
  the main BOM. It has no default address: `OllamaClient` calls
  `ensureNotBlank(baseUrl, "baseUrl")`, and no `localhost` or `11434` string exists in the jar.
- **The OpenAI client does not require a key.** `DefaultOpenAiClient` calls `ensureNotBlank` on
  `baseUrl` and adds the `Authorization` header only when `apiKey` is not null. So in the chain
  from a block with `provider = openai` to a local server, the only thing that demands a key
  is this library.
- **`langchain4j-local-ai` is a wrapper around the OpenAI client.** It depends on
  `langchain4j-open-ai`, `LocalAiChatModel` calls
  `dev.langchain4j.model.openai.internal.OpenAiClient`, and it is on the beta line,
  `1.20.0-beta30`.

Fixed before this decision: a factory reports a capability and core enforces it, so every
provider refuses the same configuration in the same words (ADR-0048); a provider's `validate()`
is for a rule core cannot see (ADR-0049); the schema does not grow a key because one provider's
client has more settings (ADR-0030); and a discriminator needs two real variants today
(ADR-0010).

## Forces

**Declare it or validate it in each provider.** Each provider's `validate()` could reject a
missing or unwanted key. That is the shape ADR-0048 removed: four copies of one rule, four
wordings of one message, and a rule that can drift in one module. Whether a key may and must be
set is the same question for every provider, so it belongs in core. What is particular to one
provider stays in its `validate()`.

**`base-url` against ADR-0030.** ADR-0030 refused provider-specific keys: a `glm { … }` block
would make the schema grow with every provider and make a file non-portable between them.
`base-url` is not that. It is honoured by every one of the 11 builders listed above, so it is
not specific to any provider, and it chooses *which server answers*, not how a request is
shaped — it is as much a part of the connection as `model-name`.

**Two booleans or one three-state type.** `canHaveApiKey()` and `mustHaveApiKey()` were
considered, and so were `usesApiKey()` and `hasDefaultEndpoint()`. The second pair has two
states per key and cannot express `openai`: its key is needed with `api.openai.com` and not with
a local server, so it is neither required nor forbidden. The first pair can express it, but has
four combinations and one of them — required but not permitted — is illegal, and nothing stops a
third-party factory from declaring it; core would have to detect it at run time. A type with
three values makes that combination unwritable. It also matches how this SPI already states a
three-state fact: `TokenEstimation`.

**ADR-0010 is met by `api-key` alone.** `MANDATORY` is Anthropic, Gemini and GLM; `OPTIONAL` is
OpenAI; `FORBIDDEN` is Ollama. Each value has a real provider. `base-url` reuses the type and
needs two of its values: `MANDATORY` for Ollama, `OPTIONAL` for the rest.

**Abstract or `default`.** ADR-0048 gave `supportsModeration()` a default so that adding it would
not break an existing implementer, and accepted that the default is chosen "for compatibility
rather than for truth". That reason is absent here. `LlmConfig.apiKey()` changes type, and the
four modules call it in 12 places; every implementer is recompiled and edited whatever the SPI
does. A default of `MANDATORY` would then only hide the question: a factory for a server with no
credential that forgot to override it would refuse every block that correctly leaves `api-key`
out. The existing three-state method, `tokenEstimation()`, is abstract.

**A module for LocalAI.** Rejected. ADR-0022 took a beta-line module for GLM "because no stable
equivalent exists". For LocalAI one exists and is the same client: `provider = openai` with a
`base-url`. The module would duplicate it and cost the beta line.

## Decision

1. **`KeyRequirement`** is a new public enum in the SPI package with three values, `FORBIDDEN`,
   `OPTIONAL` and `MANDATORY`, and two methods: `permitted()`, true unless `FORBIDDEN`, and
   `required()`, true only for `MANDATORY`. The names are neutral because one type serves both
   keys.
2. **`ProviderFactory` gains two abstract methods**, `apiKeyRequirement()` and
   `baseUrlRequirement()`, both returning `KeyRequirement`. Neither has a default.
3. **`LlmConfig.apiKey()` becomes `Optional<String>`, and `LlmConfig` gains
   `Optional<String> baseUrl()`**, read from a new optional key `base-url`. A present value may
   not be blank, like every other text value in the record.
4. **Core enforces both requirements in `SnapshotLoader.validateCapabilities`**, before the
   factory's own `validate()`, with two messages parameterised by the key's name: one for a key
   that is set where the provider does not permit it, one for a required key that is missing.
   Both name the block and the provider, as the two capability messages there already do. A
   factory that returns `null` for either method is refused, as one that returns `null` from
   `tokenEstimation()` already is.
5. **`base-url` reaches every builder the factory calls for that block**, not only the chat
   model. A block behind a proxy that sent its moderation or its remote token count to the
   vendor's default address would reach two servers from one configuration.
6. **The four existing providers declare:** `openai` — `api-key` `OPTIONAL`, `base-url`
   `OPTIONAL`; `anthropic`, `gemini`, `glm` — `api-key` `MANDATORY`, `base-url` `OPTIONAL`.
   **`openai` adds one rule in its `validate()`**: with no `base-url` its client calls
   `api.openai.com`, which requires a key, so a block with neither is refused. The rule is about
   the address the factory itself chooses when none is given, which core cannot see.
7. **An Ollama provider** declares `api-key` `FORBIDDEN` and `base-url` `MANDATORY`, on
   `langchain4j-ollama` from the main BOM.
8. **LocalAI, llama.cpp, vLLM and LM Studio are reached through `provider = openai`** and a
   `base-url`. No module is added for any of them.

## Consequences

- **The only source break of this change.** Code that reads `config.apiKey()` as a
  `String` stops compiling, and a third-party `ProviderFactory` stops compiling until it
  implements the two methods. The CHANGELOG allows a break in a minor while the version is
  `0.x`, and states the migration.
- **A binary break for a third-party factory compiled against `0.2.0`.** It fails at run time
  rather than at load: with `AbstractMethodError` where core calls one of the two new methods,
  and with `NoSuchMethodError` where the factory calls `config.apiKey()`, whose return type
  changed. That is the accepted cost of having no default; the CHANGELOG says so, because a
  reader of a source break does not expect a failure at run time.
- **Configuration files that load today still load.** Every one sets `api-key`, and no
  existing provider forbids it. `base-url` is new, so no file sets it yet.
- **An `openai` block pointed at a local server needs no key.** Ollama's documentation of its
  OpenAI-compatible endpoint shows a placeholder key marked "required but ignored" — required
  by the OpenAI SDKs for Python and JavaScript, not by the Java client. With this library it is
  not needed, and writing one is harmless because `OPTIONAL` permits it.
- **`FORBIDDEN` has no provider until the Ollama module exists.** The two ship in the same
  version, so no released artifact has a value with nothing behind it. A release between them
  would.
- **Not covered: a credential that is not a single key.** `FORBIDDEN` means the provider takes
  no `api-key`; it does not mean the library supports any other way to authenticate. The same
  BOM carries `langchain4j-bedrock`, `langchain4j-vertex-ai-gemini`, `langchain4j-watsonx` and
  `langchain4j-azure-open-ai`, whose builders have not been read. Whether any of them fits one
  `api-key` and one `base-url` is an open question, and one that does not needs a decision of
  its own.
- **Not covered: inference inside the JVM.** `JlamaChatModel` downloads its model if it is
  absent and loads it, in its constructor; `GPULlama3ChatModel` loads its model file in its
  constructor too, through `ModelLoader.loadModel`. Both modules are on the beta line. A
  reload builds the new bundle beside the old one before the swap (ADR-0008), so editing any
  key of such a block would load a second copy of the model. That is a mismatch
  with the reload model, not with this schema, and no value of `KeyRequirement` changes it.
- **Do not add a `default` to either method** to spare an implementer. It trades a compile error
  for a wrong answer that appears only when a block omits the key.
- **Do not move the missing-key rule into each provider's `validate()`.** That is the duplication
  ADR-0048 removed. The `openai` rule is the exception because it depends on `base-url`, which
  is `openai`'s own fact.
