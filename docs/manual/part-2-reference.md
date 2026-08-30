# Part 2 — Reference

What every key means, what every method promises, and what the library does when things go
wrong. [Part 1](part-1-tutorial.md) is the way in; this is the page you come back to.

**Contents**

| | |
|---|---|
| [Concepts](#concepts) | five words used precisely |
| [Dependencies](#dependencies) | what to put in your POM |
| [Examples](#examples) | four runnable programs, one claim each |
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

## Dependencies

**Java 17 or newer.** Built and tested on 17, 21 and 25.

**Not on Maven Central yet.** Build and install locally from a checkout:

```bash
git clone https://github.com/maxtrezzi/modelrack4j.git
cd modelrack4j && mvn install
```

Import the BOM once, then declare artifacts without versions:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.maxtrezzi</groupId>
      <artifactId>modelrack4j-bom</artifactId>
      <version>0.1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.github.maxtrezzi</groupId>
    <artifactId>modelrack4j-core</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.maxtrezzi</groupId>
    <artifactId>modelrack4j-provider-anthropic</artifactId>
  </dependency>
</dependencies>
```

| Artifact | Take it when |
|---|---|
| `modelrack4j-core` | always |
| `modelrack4j-provider-openai` | you configure `provider = openai` |
| `modelrack4j-provider-anthropic` | `provider = anthropic` |
| `modelrack4j-provider-gemini` | `provider = gemini` |
| `modelrack4j-provider-glm` | `provider = glm` |
| `modelrack4j-bom` | to version all of the above from one coordinate |

**Core knows no providers.** It contains no provider artifact and never will: each provider
module registers itself through `ServiceLoader`, and a name whose `provider` has no module on
the classpath is a configuration error that lists the providers actually available. An
application configuring only Anthropic therefore never carries OpenAI's dependencies.

What core brings with it, and nothing else:

```
modelrack4j-core
+- dev.langchain4j:langchain4j-core     (+ jackson, jspecify — transitive)
+- dev.langchain4j:langchain4j          (for ChatMemoryProvider, which is not in -core)
+- com.typesafe:config
\- org.slf4j:slf4j-api
```

**Add an SLF4J binding.** Core logs through the API and ships no implementation, so without
one, SLF4J prints its no-provider notice and the [warnings](#logging) — including every
rejected reload — are discarded.

---

## Examples

Each program in [`modelrack4j-examples`](../../modelrack4j-examples) demonstrates a single
claim rather than the library in general.

| Example | Demonstrates | Needs |
|---|---|---|
| `AtomicSnapshot` | [Snapshot-wide atomicity](#reload-semantics): a single save changes two models at once, while four threads keep reading both — once via two separate `get()` calls, once via one `snapshot()` shared for both lookups. A `get()` pair can occasionally catch one model already updated and the other not (a torn read); a `snapshot()` pair never can, because both lookups read the same frozen snapshot. The counter is real, not decorative: sabotaging the swap to publish one model 5 ms early makes the `get()` count jump to tens of thousands. | **nothing** — reads configuration only, sends no request |
| `ProviderSwap` | The provider as configuration: the same method, called twice around a file edit, answered by `AnthropicChatModel` and then `OpenAiChatModel`. The method names no provider and has no branch. | `ANTHROPIC_API_KEY` + `OPENAI_API_KEY`, two requests |
| `ConsoleChat` | Everything interactively: a menu of configured models, streaming where configured, moderation on input where configured, memory across turns, and reload while you watch. | one provider key |
| `ThreeModelCouncil` | The multi-model scenario: three names, one question, capabilities read from the bundle. | two provider keys |

Run them with `exec:java` after `mvn install` — the plugin resolves `modelrack4j-core` from
`~/.m2` rather than from the reactor, so a stale local install is the usual cause of a
`NoSuchMethodError` here:

```bash
mvn install
mvn -q -pl modelrack4j-examples exec:java \
    -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.AtomicSnapshot
```

`./run-atomic.sh` from the repository root does the same thing, and installs first if it has
to. There is one script per example — `run-atomic.sh`, `run-swap.sh`, `run-chat.sh`,
`run-council.sh` — and each `--help` gives what that example shows, what it costs, which keys
it needs and the plain `mvn` command to use on Windows, since there are no `.bat`
counterparts.

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
| `description` | string | *none* | Human-readable. Nothing in the library reads it. Blank is rejected; `null` in a higher layer clears a description set in a lower layer. |
| `provider` | string | *required* | Must match a `ProviderFactory` on the classpath. An unknown value is an error that lists the providers actually available. |
| `api-key` | string | *required* | Use `${VAR}`. Never blank. |
| `model-name` | string | *required* | The provider's own identifier. **Not validated** — see below. |
| `temperature` | number | *provider's own* | 0.0–2.0. Omitted means "do not set it", which is different from setting a default. Some models reject a non-default value — see [Providers](#providers). |
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
path. There is no "skip what is missing" mode. The reason: an operator expects every listed
file to be read, and a file that was silently skipped is a worse outcome than a start that
fails immediately.

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
| **locally** (OpenAI, via a bundled tokenizer) | built, with no extra configuration |
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

On a local counter the flag has no effect rather than being an error, because one
configuration layer commonly spans several providers.

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
| `snapshot()` | The current generation, held still, as an `LlmSnapshot`. Every lookup on it belongs to that one generation. |
| `names()` | The configured names, sorted. |
| `onReload(Consumer<ReloadChange>)` | Registers a listener for successful reloads. |
| `onReloadFailure(Consumer<ReloadFailure>)` | Registers a listener for rejected ones. |
| `close()` | Stops watching. Idempotent. A closed registry keeps serving its last snapshot. |

`get()` is a volatile read, one small wrapper object, and a map lookup. It is meant to be
called per request — that is the primary API, and listeners are secondary.

### One lookup, or several that must agree

`get()` reads the live configuration on every call. That is what makes a reload visible, and
it means **a reload can land between two consecutive calls**, so the two calls return bundles
built from different file contents. It is rare — measured at roughly two per million pairs of
reads while a reload ran every few milliseconds — but it is reproducible, and where several
models have to agree it is a correctness problem rather than a cosmetic one.

`snapshot()` reads the published generation once and hands it back:

```java
LlmSnapshot models = registry.snapshot();   // one read of the current generation
var fast = models.get("SL");
var deep = models.get("SH");                // same generation as fast, guaranteed
```

| Method on `LlmSnapshot` | Returns |
|---|---|
| `get(String name)` | The bundle for that name in this generation. Throws `UnknownConfigurationException` if this generation has no such name. |
| `names()` | The names in this generation, sorted. |
| `contains(String name)` | Whether this generation has that name, without throwing. |

A snapshot **never updates**. Take one per unit of work — per request, per council round —
and drop it afterwards. Holding one for the lifetime of the application is the caching trap
in a different shape. See
[ADR-0038](../adr/0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md).

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

Those three are the library's own, and they are thrown identically whichever provider a block
names. **Everything a model call throws belongs to the provider instead**, and those types are
not portable between providers.

All four providers were called against their live API in
[P6](../tasks/post-v1.md#p6--the-integration-tests-against-live-apis). Three of the four
runs failed before they were made to pass, and those three failures are what the table
below records:

| Provider | Condition | Type thrown |
|---|---|---|
| OpenAI | account out of credit | `dev.langchain4j.exception.RateLimitException` |
| Gemini | model ID retired upstream | `dev.langchain4j.exception.ModelNotFoundException` |
| GLM | resource package out of credit | `dev.langchain4j.community.model.zhipu.ZhipuAiException` |

Read rows one and three together: the *same* real condition arrives as two different types
depending on which provider the configuration names. GLM's message is in Chinese, and its
detail code is reachable only via a provider-specific `getCode()`.

So the swap guarantee covers exactly this much. **Which objects exist, who builds them, with what
credentials, model, timeout and memory — all config-shaped, all swap freely. What a failing
call throws does not.** Catch `dev.langchain4j.exception.LangChain4jException` and your
handling survives any swap; all four providers throw beneath it. Catching anything more
specific is provider-specific code, which is fine as long as it is deliberate — after a swap
it does not fail loudly, the catch block simply stops matching.

The library does not translate these, and
[ADR-0033](../adr/0033-provider-exceptions-pass-through-untranslated.md) records why:
translating means proxying every model call, which would stop the registry handing back
genuine LangChain4j objects.

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
eligible for garbage collection when nothing references them.

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

The symlink handling is deliberately asymmetric, and it must not be refactored away. Resolving a
symlink to its real path at registration — the obvious implementation — makes a ConfigMap swap
**completely invisible**: it produces no event at all under that strategy.

### Latency

Measured on Linux (inotify, Temurin 25), write to event observed, 20 samples:

| min | median | max |
|---|---|---|
| 0.37 ms | **0.50 ms** | 0.63 ms |

Add the debounce, so a saved file is live roughly 300 ms later by default. Events for one
logical write arrive within ~2.5 ms, which is the burst the default is chosen to cover. Lowering it
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
which is released on the community cycle, separately from the stable modules.

**Per-provider notes:**

- **OpenAI** — the moderation endpoint takes its own model, not your chat model, so
  `model-name` is deliberately not forwarded to it. Local token counting needs a model the
  bundled tokenizer recognises; an unknown one is reported as a configuration error naming the
  model, rather than surfacing later during memory eviction.
- **Anthropic** — some models reject a non-default `temperature`. `claude-sonnet-5` answers
  one with HTTP 400, because the model's adaptive thinking controls its own sampling;
  `claude-sonnet-4-6` still accepts one. The schema takes any value from 0.0 to 2.0, so this
  is the provider's rule rather than this library's, and you meet it on the first request.
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
ships an estimator, so a boolean would return true almost everywhere and accept every
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
- **Listeners may be registered at any time, from any thread.** The registration lists are
  copy-on-write, so adding one during a reload is safe and does not block it.
- **Registering a listener does not replay anything.** It is called on the next reload, never
  for one that already happened; the current state is `get()`, not a callback.
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
| `` `temperature` is deprecated for this model `` | A non-default `temperature` on a model that rejects one, such as `claude-sonnet-5` | Remove the key. The model then uses its own sampling settings. |
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
