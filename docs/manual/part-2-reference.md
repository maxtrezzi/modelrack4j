# Part 2 — Reference

What every key means, what every method promises, and what the library does when things go
wrong. [Part 1](part-1-tutorial.md) is the way in; this is the page you come back to.

**Contents**

| | |
|---|---|
| [Concepts](#concepts) | five words used precisely |
| [Configuration](#configuration) | file format, layering, every key |
| [Memory](#memory) | the two variants and the cost rule |
| [Java API](#java-api) | builder, registry, records, exceptions |
| [Reload semantics](#reload-semantics) | exactly what is guaranteed |
| [The watcher](#the-watcher) | what it sees, and how fast |
| [Logging](#logging) | what is reported where |
| [Providers](#providers) | the capability matrix, and adding your own |
| [Threading and lifecycle](#threading-and-lifecycle) | threads, closing, in-flight requests |
| [Out of scope](#out-of-scope) | what this will never do |
| [Troubleshooting](#troubleshooting) | symptom, cause, fix |
| [Versioning](#versioning) | what 0.x means here |

---

## Concepts

| Term | Meaning |
|---|---|
| **Name** | A key under `llm` in the configuration — `SL`, `CR`, `summariser-eu`. You invent it, your code asks for it, and it is the registry's only key. Two names may use the same provider and the same model, differing only in parameters. |
| **Bundle** | Everything built from one name: a `ChatModel`, and optionally a `StreamingChatModel`, a `ModerationModel` and a `ChatMemoryProvider`. Immutable. |
| **Snapshot** | The complete map of name to bundle at one instant. There is exactly one live snapshot, and a reload replaces it wholesale. |
| **Layer** | One configuration file. Layers merge into one snapshot; they do not each produce their own. |
| **Provider** | A LangChain4j integration, wrapped in a `ProviderFactory` and discovered on the classpath. Never a registry key. |

---

## Configuration

HOCON, parsed by [Typesafe Config](https://github.com/lightbend/config). Every named block
lives under the `llm` root.

```hocon
llm {
  <name> {
    description = "..."          # optional
    provider    = anthropic      # required
    api-key     = ${SOME_VAR}    # required
    model-name  = "..."          # required
    ...
  }
}
```

### Every key

| Key | Type | Default | Notes |
|---|---|---|---|
| `description` | string | *none* | Human-readable. Nothing in the library reads it. Blank is rejected; `null` in a higher layer clears one set lower down. |
| `provider` | string | *required* | Must match a `ProviderFactory` on the classpath. An unknown value lists the ones that are. |
| `api-key` | string | *required* | Use `${VAR}`. Never blank. |
| `model-name` | string | *required* | The provider's own identifier. **Not validated** — see below. |
| `temperature` | number | *provider's own* | 0.0–2.0. Omitted means "do not set it", which is different from setting a default. |
| `timeout` | duration | `60s` | HOCON durations: `30s`, `2m`, `500ms`. Must be positive. |
| `streaming` | boolean | `false` | Builds a `StreamingChatModel` alongside the chat model. |
| `log-requests` | boolean | `false` | The provider logs requests. **Puts prompts, and therefore user data, in your logs.** |
| `log-responses` | boolean | `false` | Same caveat. |
| `memory.type` | string | *no memory* | `message-window` or `token-window`. |
| `memory.max-messages` | int | — | Required by `message-window`, greater than zero. |
| `memory.max-tokens` | int | — | Required by `token-window`, greater than zero. |
| `memory.allow-remote-token-counting` | boolean | `false` | See [Memory](#memory). |
| `moderation.enabled` | boolean | `false` | Builds a `ModerationModel`. Rejected on providers that ship none. |

Defaults live in
[`modelrack4j-reference.conf`](../../modelrack4j-core/src/main/resources/modelrack4j-reference.conf)
inside the core jar. It is deliberately not named `reference.conf`: HOCON merges that
automatically only for fixed paths, and your configuration names are not fixed, so the loader
merges the defaults into each named block explicitly.

**Model names are not validated.** `model-name` goes straight to the provider's builder.
LangChain4j ships model-name enums, but they are a convenience rather than a whitelist, and
being compiled at release time they lag the providers — a model released after LangChain4j
1.19.0 works fine here. The cost is that a typo surfaces as the provider's own error on the
first request rather than at load time.

### Layering

Files are given **lowest precedence first**; the last one wins on conflict.

```java
.configFiles(List.of(
        Path.of("/etc/myapp/defaults.conf"),   // baseline
        Path.of("/etc/myapp/prod.conf"),       // environment
        Path.of("./local.conf")))              // developer override
```

Every layer is parsed **without resolution**, merged, and resolved **exactly once** at the
end. Three consequences, all load-bearing:

1. A `${VAR}` in a lower layer that a higher layer replaces is never evaluated. You can ship a
   baseline demanding `${ANTHROPIC_API_KEY}` and override that key entirely on a machine where
   the variable does not exist.
2. A substitution can refer to a key that only a higher layer defines.
3. Resolving each file as you parse it — the natural-looking implementation — breaks both. It
   has its own regression suite for that reason.

Substitutions fall back to environment variables, so `${VAR}` reads the environment and fails
loudly when unset. `${?VAR}` is the optional form and is the wrong tool for a credential: it
yields nothing and defers the failure to the first request.

To clear a value a lower layer set, rather than override it, use `null` — HOCON removes the
key outright.

### Missing and malformed files

A layer that does not exist, or cannot be read, is a `ConfigValidationException` naming the
path. There is no "skip what is missing" mode: a configuration file the operator expected to
be read and which silently was not is a worse outcome than a failed start.

---

## Memory

Memory is built only when a `memory` block is present. The bundle then carries a
`ChatMemoryProvider`; the library never stores conversations itself.

**`message-window`** counts messages. It needs nothing from the provider and works everywhere.

```hocon
memory { type = message-window, max-messages = 20 }
```

**`token-window`** counts tokens, which requires the provider to supply a
`TokenCountEstimator` — and what that costs varies by provider:

| The provider counts | Behaviour |
|---|---|
| **locally** (OpenAI, via a bundled tokenizer) | built, no ceremony |
| **remotely** (Anthropic, Gemini) | **rejected unless** `allow-remote-token-counting = true` |
| **not at all** (GLM) | rejected outright; the flag does not apply |

```hocon
memory { type = token-window, max-tokens = 2000, allow-remote-token-counting = true }
```

The middle row is the one that matters. `TokenWindowChatMemory` calls the estimator on
eviction, so on a remote counter every ordinary conversation turn can make a billed,
rate-limited, network-dependent HTTP call inside what your application treats as in-memory
bookkeeping. That is a decision, so it is opted into explicitly rather than discovered on a
bill.

On a local counter the flag is inert rather than an error, because one configuration layer
commonly spans several providers.

---

## Java API

### Building a registry

```java
LlmRegistry registry = LlmRegistry.builder()
        .configFiles(List.of(path, higherPrecedencePath))   // required, lowest first
        .watch(true)                                        // default false
        .debounce(Duration.ofMillis(300))                   // default 300 ms
        .build();
```

| Method | Contract |
|---|---|
| `configFiles(List<Path>)` | The layers, lowest precedence first. At least one. |
| `watch(boolean)` | Off by default. On, the registry starts one daemon thread. |
| `debounce(Duration)` | How long the files must be quiet before a reload runs. Must be positive. |
| `build()` | Parses, validates and builds everything, then starts watching. Throws `ConfigValidationException` if any layer or block is invalid, or `UncheckedIOException` if a directory cannot be watched. |

`build()` is all-or-nothing: one bad block means no registry, not a registry missing one name.

### Using it

| Method | Returns |
|---|---|
| `get(String name)` | The current bundle. Throws `UnknownConfigurationException` if the name is not configured *now*. |
| `names()` | The configured names, sorted. |
| `onReload(Consumer<ReloadChange>)` | Registers a listener for successful reloads. |
| `onReloadFailure(Consumer<ReloadFailure>)` | Registers a listener for rejected ones. |
| `close()` | Stops watching. Idempotent. A closed registry keeps serving its last snapshot. |

`get()` is a volatile read and a map lookup. It is meant to be called per request — that is
the primary API, and listeners are secondary.

### Records

```java
record LlmBundle(LlmConfig config,
                 ChatModel chatModel,
                 Optional<StreamingChatModel> streamingChatModel,
                 Optional<ModerationModel> moderationModel,
                 Optional<ChatMemoryProvider> chatMemoryProvider) {
    String name();          // == config().name()
}

record LlmConfig(String name, Optional<String> description, String provider, String apiKey,
                 String modelName, Optional<Double> temperature, Duration timeout,
                 boolean logRequests, boolean logResponses, boolean streaming,
                 Optional<MemoryConfig> memory, boolean moderationEnabled) { }

sealed interface MemoryConfig {
    record MessageWindow(int maxMessages) implements MemoryConfig { }
    record TokenWindow(int maxTokens, boolean allowRemoteTokenCounting) implements MemoryConfig { }
    String typeName();
}

record ReloadChange(Set<String> updated, Set<String> added, Set<String> removed) {
    boolean isEmpty();
}

record ReloadFailure(List<Path> configFiles, Exception cause) { }
```

`LlmConfig` validates in its compact constructor, so an instance that exists is valid.
`MemoryConfig` is sealed with a record per variant rather than one record carrying unused
fields — `max-messages` and `max-tokens` cannot both be set, because no type has both.

Value equality on `LlmConfig` is load-bearing: it is how a reload decides what changed. Every
component participates, including `description`.

### Exceptions

| Exception | When |
|---|---|
| `ConfigValidationException` | A file is unreadable, a block is malformed, a value is out of range, a substitution is unresolved, or a provider rejects its configuration. Unchecked. |
| `UnknownConfigurationException` | `get()` on a name that is not in the current snapshot. |
| `UncheckedIOException` | Watching was requested and a directory cannot be watched. |

---

## Reload semantics

A reload runs when a watched file has been quiet for the debounce period. It then:

1. re-parses every layer, merges, resolves once;
2. parses each named block into an `LlmConfig`;
3. compares each against the live one **by record equality**, and rebuilds only what differs;
4. assembles a complete new snapshot in a staging area;
5. swaps one reference.

**What is guaranteed:**

- **All or nothing, snapshot-wide.** Any failure at any step means nothing swaps. The previous
  snapshot stays live in full, and `onReloadFailure` fires exactly once. A half-applied
  configuration never exists, even briefly.
- **One callback per reload, never one per name.** `onReload` fires once with `updated`,
  `added` and `removed`. Per-name notifications are derivable from that object; firing them
  separately would let an application observe a new `SL` beside an old `SH`, which is a
  correctness hazard for multi-model flows rather than a cosmetic one.
- **Unchanged bundles are the same object.** Not merely equal — identical. Editing one block
  does not rebuild the others.
- **A callback means something changed.** A save that resolves to an identical snapshot swaps
  nothing and notifies nobody. It is not a heartbeat.
- **Listeners cannot break reloading.** An exception from a listener is caught and logged; the
  other listeners still run, and so do later reloads.
- **Listeners run after the swap**, on the watcher thread, so `get()` inside one already sees
  the new snapshot.

**Names appearing and disappearing.** A name added to the file appears in the registry; a name
removed from it is removed, and `get()` on it then throws. Long-running code holding a name
must be ready for that — the console example catches it and returns to its menu.

**Superseded bundles are not closed.** An in-flight request may still hold one. They become
collectable when nothing references them.

---

## The watcher

`WatchService` registers on **directories**, not files, so the watcher registers the
deduplicated set of parent directories and filters events by filename.

| Case | Handling |
|---|---|
| In-place rewrite | `ENTRY_MODIFY`, one or two events. |
| Temp-file-then-rename | Arrives as `ENTRY_CREATE`; the `.tmp` events are filtered out by name. |
| Burst from one save | Collapsed by the debounce, which is trailing-edge: each event pushes the deadline out. |
| Symlinked config path | The **symlink's own directory** is watched, not the resolved target's. |
| Kubernetes ConfigMap swap | Seen. No event in that swap is named after your config file, so filename filtering is switched off for symlinked paths. |
| Watched directory deleted | Re-registration is retried once a second until it succeeds. |
| `OVERFLOW` | Treated as "something changed": a reload is scheduled. |

The symlink handling is deliberately asymmetric and is not a tidy-up candidate. Resolving a
symlink to its real path at registration — the obvious implementation — makes a ConfigMap swap
**completely invisible**: it produces no event at all under that strategy.

### Latency

Measured on Linux (inotify, Temurin 25), write to event observed, 20 samples:

| min | median | max |
|---|---|---|
| 0.37 ms | **0.50 ms** | 0.63 ms |

Add the debounce, so a saved file is live roughly 300 ms later by default. Events for one
logical write arrive within ~2.5 ms, which is what the default is sized against. Lowering it
below the time your writer takes to finish produces reloads of half-written files, which are
rejected as failures rather than applied.

> **macOS is not measured.** The JDK's `WatchService` there is polling-based internally, so
> latency is expected to be substantially higher — seconds, not sub-milliseconds. Nothing in
> the design depends on the figure, but if you deploy on macOS, measure it rather than trusting
> this page.

---

## Logging

Core logs through **`slf4j-api`** and ships no binding. Add one, or SLF4J prints its
no-provider notice and everything below is discarded.

| Logger | Level | Event |
|---|---|---|
| `io.github.maxtrezzi.modelrack4j.LlmRegistry` | `WARN` | A reload was rejected, with the cause. **Logged whether or not you registered `onReloadFailure`.** |
| `io.github.maxtrezzi.modelrack4j.LlmRegistry` | `ERROR` | A listener threw. The reload itself is unaffected. |
| `io.github.maxtrezzi.modelrack4j.ConfigWatcher` | `ERROR` | A reload callback threw. Watching continues. |
| `io.github.maxtrezzi.modelrack4j.ConfigWatcher` | `DEBUG` | A watched directory cannot be re-registered yet; the watch service did not close cleanly. |

Successful reloads are **not** logged. If you want that, register `onReload` and log what it
gives you.

The unconditional `WARN` is deliberate. A typo in a config file does not delay one reload — it
makes every later edit to that file fail identically, so without the log the only symptom is a
model that quietly stops reflecting the file.

---

## Providers

Each provider is a separate module. Core takes **no** provider artifact, so an application
configuring only Anthropic never has OpenAI's dependencies on its classpath.

| Module | `provider =` | Chat | Streaming | Moderation | Token estimation |
|---|---|---|---|---|---|
| `modelrack4j-provider-openai` | `openai` | ✅ | ✅ | ✅ | **local** |
| `modelrack4j-provider-anthropic` | `anthropic` | ✅ | ✅ | ❌ | remote |
| `modelrack4j-provider-gemini` | `gemini` | ✅ | ✅ | ❌ | remote |
| `modelrack4j-provider-glm` | `glm` | ✅ | ✅ | ❌ | none |

Read out of the LangChain4j 1.19.0 artifacts rather than from documentation. Gemini is the
stable `langchain4j-google-ai-gemini` module; GLM comes from `langchain4j-community-zhipu-ai`,
which is on the community release train.

**Per-provider notes:**

- **OpenAI** — the moderation endpoint takes its own model, not your chat model, so
  `model-name` is deliberately not forwarded to it. Local token counting needs a model the
  bundled tokenizer recognises; an unknown one is reported as a configuration error naming the
  model, rather than surfacing later during memory eviction.
- **GLM** — has no whole-call timeout. Its client's `callTimeout` and `writeTimeout` are
  deprecated and marked for removal upstream, so `timeout` maps to connect and read only. Its
  `ChatModel.provider()` returns `OTHER`, so an application routing on that cannot tell GLM
  from any other community model — the registry name is the reliable discriminator.
- **All providers except OpenAI** — moderation is unavailable, and enabling it is a
  configuration error.

**One upstream limitation to know:** LangChain4j silently ignores moderation on the
`AiServices` streaming path ([#2779](https://github.com/langchain4j/langchain4j/issues/2779)).
This library only builds the objects; if you configure `streaming = true` alongside
`moderation.enabled = true` and wire the bundle into `AiServices`, moderation will not run on
that path.

### Adding a provider

Implement `io.github.maxtrezzi.modelrack4j.spi.ProviderFactory` and register it in
`META-INF/services/io.github.maxtrezzi.modelrack4j.spi.ProviderFactory`.

```java
public interface ProviderFactory {
    String providerId();                                              // matched against `provider =`
    TokenEstimation tokenEstimation();                                // ABSENT | LOCAL | REMOTE
    void validate(LlmConfig config);                                  // capability checks, fail fast
    ChatModel createChatModel(LlmConfig config);
    Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config);
    Optional<ModerationModel> createModerationModel(LlmConfig config);
    Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config);
}
```

`tokenEstimation()` is three-valued rather than boolean on purpose. Every provider except GLM
ships an estimator, so a boolean would return true almost everywhere and bless every
configuration — the check would exist and catch nothing. What varies is the *cost*.

`validate()` is where capability mismatches are caught. Throw `ConfigValidationException` with
a message naming the block and the way out; those messages are part of the contract, and the
tests assert on them.

---

## Threading and lifecycle

- **One daemon thread**, named `modelrack4j-config-watcher`, and only when `watch(true)`. With
  watching off, the library starts nothing.
- **That thread is the only writer.** The live snapshot is a single volatile field and
  publishing is one assignment, so there is no lock anywhere in the reload path.
- **`get()` and `names()` are safe from any thread**, during a reload included. A reader either
  sees the whole old snapshot or the whole new one.
- **Listeners run on the watcher thread.** Long work in a listener delays the next reload;
  hand it off to your own executor if it is not quick.
- **`close()` waits** for a reload already in flight, so no listener runs after it returns. It
  is safe to call from a listener — that case is detected rather than deadlocking.
- **Bundles are never closed by the library**, including superseded ones.

---

## Out of scope

Deliberate and permanent:

- **`AiServices`, `@Tool` methods, RAG retrievers, guardrails.** Code-shaped, not
  config-shaped. This library builds the inputs you pass to `AiServices`; it does not wrap it.
- **Provider pools, fallback, retry, circuit breaking.** Resilience4j owns that.
- **Generic reloadable configuration.** Apache Commons Configuration owns that.
- **`EmbeddingModel`** — not in v1.
- **A `ReloadableChatModel` hot-swap wrapper** — designed for, deferred. Hot *reload* is here
  today; only the wrapper that would hide `registry.get()` is deferred.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Edits to the file change nothing, and no error appears | A bundle or model was cached in a field at startup | Call `registry.get(name)` per use. Inject the registry, not a `ChatModel`. |
| Edits change nothing, and no log line either | `watch(true)` was never set, or no SLF4J binding is on the classpath | Enable watching; add a binding. |
| `A mandatory substitution is unresolved` | `${VAR}` with the variable unset | Export it, or override the key in a higher layer. Do not switch to `${?VAR}`. |
| `ships no moderation model` | `moderation.enabled = true` on Anthropic, Gemini or GLM | Only OpenAI has one. Route moderation through an OpenAI configuration. |
| `counts tokens by calling its API` | `token-window` on a remote counter | Add `allow-remote-token-counting = true`, or use `message-window`. |
| `no token count estimator` | `token-window` on GLM | Use `message-window`. No flag helps. |
| `UnknownConfigurationException` at runtime | The name was removed from the file while running | Catch it and re-read `names()`, or keep the block. |
| Reloads fire constantly | Something else writes into a watched directory | Only the configured filenames are matched, but a symlinked path matches any event in its directory by design. |
| Half-written files are rejected as failures | The debounce is shorter than your writer takes | Raise `debounce(...)`. |
| `NoSuchMethodError` running an example | A stale `modelrack4j-core` in `~/.m2` | `mvn install` from the checkout root. |
| The default build fails asking for an API key | An integration test escaped its guard | ITs run only under `-Pintegration` and skip themselves without their key. Report it: the default build must pass offline. |

---

## Versioning

**`0.x`, and pre-1.0 rules apply.** The API may change in a minor release while the version
starts with `0`. Breaking changes are called out in
[CHANGELOG.md](../../CHANGELOG.md) under **Changed**, with the migration, but they are not held
back for a major bump until the API settles at `1.0.0`.

**Not published to Maven Central yet.** Build and `mvn install` locally.

**LangChain4j is pinned** at 1.19.0, imported through both its stable and community BOMs. The
capability matrix on this page is a fact about that version, not a permanent property — it is
re-verified against the artifacts on every bump.

---

## See also

- **[Part 1 — Tutorial](part-1-tutorial.md)** — the guided path from nothing to a running
  console chat.
- **[`docs/adr/`](../adr/README.md)** — why each of these decisions was made, and what was
  rejected. Every rule on this page has an ADR behind it.
