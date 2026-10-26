# ADR-0063: Model listeners are registered on the registry, per configuration, and reach the factory in a context

- **Status:** Accepted
- **Date:** 2026-10-26
- **Supersedes:** —
- **Amends:** —

## Context

The registry builds every model and builds it again when a reload changes its configuration,
so an application has no place to attach a `ChatModelListener`. That rules out the
observability LangChain4j already offers: `langchain4j-micrometer-metrics`,
`langchain4j-observation`, or a listener of the application's own that logs requests. Before
this decision no factory passed listeners, and the only listeners in the project were
`onReload` and `onReloadFailure`, which observe the registry rather than a model call.

What was read from the `1.22.0` artifacts with `javap` on 2026-10-26, and is fixed here:

- **Every chat and streaming builder the five factories call takes `listeners(...)`**, and
  every model built returns the same list from `listeners()`, which `ChatModel` and
  `StreamingChatModel` declare. `OpenAiModerationModel` does the same with
  `ModerationModelListener`, a different type that `ModerationModel.listeners()` declares. The
  two remote token estimators take no listener, and LangChain4j defines none for an estimator.
- **LangChain4j calls the listeners itself, around a call.** `ChatModel.chat(ChatRequest)` is a
  default method that reads `listeners()`, calls each in list order through
  `ChatModelListenerUtils`, and catches and logs an `Exception` a listener throws. Listeners
  are never called while a model is built.
- **A listener cannot tell which configuration it observes.** Its context carries the request,
  a `ModelProvider` and an `attributes()` map. That map is created per call from the
  *caller's* `ChatRequestOptions.listenerAttributes()`, so the registry cannot put anything in
  it: the application calls the model directly. Two blocks may share a provider and a model
  and differ only in parameters (ADR-0006), so nothing in the context separates them.

Two constraints were already fixed. The reload diff is record equality on `LlmConfig`
(ADR-0006), so nothing that is not a configuration value may enter it, which is why ADR-0055
kept the parsed custom-properties object out of the record. And `0.4.0` is a minor of a `0.x`
project, so a break is allowed, but a published version is permanent.

## Forces

**How a factory receives the listeners.** Three shapes were weighed.

- *A parameter on each `create` method.* Every later addition of the same kind would be a
  second break of the same methods.
- *A context object replacing the parameter list*, `createChatModel(LlmConfig, ModelContext)`,
  as a break like ADR-0062's. One method per model, and the next addition is a method on the
  context. Every `ProviderFactory` outside the project stops compiling, and one compiled
  against `0.3.0` throws `AbstractMethodError` at the first build.
- *The context object as an overload with a default* that calls the existing one-argument
  method. No factory breaks. The cost is two methods per model in the SPI for as long as the
  old ones stay.

ADR-0062 refused a default for the key requirements because any default would be a wrong
answer for some provider, and a wrong answer there is silent. The default here would be
equally wrong — it drops the listeners — but the drop can be detected: a model reports its
listeners through `listeners()`, so core can compare what it gave with what came back. That
turns the silent case into a refusal with a message, and only for an application that
registers listeners. With the check in place, the default costs nothing in correctness, and
the owner chose it over the break.

**How a listener learns its configuration.** Three shapes were weighed.

- *A fixed list, with nothing more.* Two blocks on one model would be indistinguishable to the
  listener, which defeats per-configuration metrics.
- *A fixed list, plus a listener of core's own placed first, which writes the block's name
  into `attributes()` under a public key.* It relies on LangChain4j passing one map to every
  listener of a call. The `1.22.0` jar does — `ChatModelListenerUtils` builds one context and
  hands it to each listener — but the project would then depend on that order and that sharing,
  and it puts a listener the application did not register into every model's `listeners()`.
- *A function from the configuration to the listeners*, called for each bundle the registry
  builds. The application binds the name — or the provider, or the model — into the listener
  itself, and nothing hidden is added. The simple case stays one lambda that ignores its
  argument.

The function was chosen. It also leaves the door open to different listeners per block,
which the other two cannot express.

**Whether moderation is covered now.** It needs a second registration method because the type
differs. P56 adds Mistral, a second provider with a moderation model, and writing it against a
context that already carries moderation listeners avoids a second change to the SPI in the
same release. Covered now.

## Decision

1. **`LlmRegistry.Builder` gains `chatModelListeners(Function<? super LlmConfig, ? extends
   List<? extends ChatModelListener>>)` and `moderationModelListeners(...)`, the same shape for
   `ModerationModelListener`.** Both are optional. The chat listeners go to the chat model and
   to the streaming chat model of a bundle; LangChain4j uses one type for both.
2. **The functions are called once per bundle the registry builds**: at `build()` for every
   configuration, and at a reload for each configuration that changed. A bundle carried over
   unchanged keeps the listeners it was built with and does not call them again, which is
   consistent because the functions are fixed at `build()`.
3. **The listeners never enter `LlmConfig`**, so they change no `ReloadChange`.
4. **A function that throws, returns `null`, or returns a list holding `null` fails the build
   or the reload with a `ConfigValidationException` naming the configuration**, as a
   custom-properties handler that throws does (ADR-0055). Nothing is swapped.
5. **`ProviderFactory` gains `createChatModel(LlmConfig, ModelContext)`,
   `createStreamingChatModel(LlmConfig, ModelContext)` and
   `createModerationModel(LlmConfig, ModelContext)`, each a default that calls the
   one-argument method.** Core calls only the two-argument forms. A factory written for this
   version overrides them and implements the one-argument forms as a call with
   `ModelContext.empty()`. `createTokenCountEstimator` takes no context.
6. **`ModelContext` is a final class in the SPI package, built through `ModelContext.of(...)`
   and `ModelContext.empty()`, not a record**, so that adding a member later breaks nobody who
   constructs one.
7. **Core checks every model it builds against the context it gave:** if the model's
   `listeners()` does not contain every listener given, the build is refused with a message
   that names the provider and says the factory was probably built for an older version. A
   model may report more listeners than it was given.

## Consequences

- An application can attach LangChain4j's own observability, or a listener of its own, to
  every model the registry builds, and it survives every reload.
- A factory written before `0.4.0` keeps compiling and working. It fails only when the
  application registers listeners, and then with a message rather than with listeners that
  never fire. That is a weaker promise than the compile error ADR-0062 chose, accepted because
  the check makes the silent case impossible.
- The SPI carries two `create` methods per model, and a factory written for this version
  implements both. The next thing a factory needs from the registry goes on `ModelContext`,
  not into another overload.
- Do not remove the check in point 7 as redundant with the five factories in this repository,
  which all pass the listeners. It is what makes the defaults in point 5 safe for factories the
  project does not own; without it, the default silently drops every listener.
- Do not move the listeners, or the functions, into `LlmConfig` to make them visible to a
  factory through the record. Every block would then compare unequal on every reload unless the
  application's listeners implemented a value `equals`, and every model would be rebuilt.
- The rule that *a factory reports and core enforces* (ADR-0048) is unchanged: a factory now
  also *receives* something, and core checks that it used it.
