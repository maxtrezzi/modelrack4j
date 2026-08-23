# modelrack4j

Turn layered HOCON configuration into **named, ready-to-use bundles of LangChain4j
objects**, with validated hot reload.

Declare `SL`, `SH` and `CR` in a config file; ask the registry for each by name and get a
consistent `ChatModel` + `StreamingChatModel` + `ModerationModel` + `ChatMemoryProvider`
per name. Edit the file and the registry picks the change up atomically, validated, without
a restart.

> **Unofficial and independent.** modelrack4j is not affiliated with, endorsed by, or part
> of the LangChain4j project. It depends on LangChain4j; it does not speak for it. That is
> also why no artifact here uses the `langchain4j-` prefix.

**Status: pre-release, `0.1.0-SNAPSHOT`.** Not published to Maven Central yet — build and
install it locally (see [Building](#building-from-source)). The API is 0.x and may still
change; see [CHANGELOG.md](CHANGELOG.md).

---

## Why

LangChain4j gives you model builders. What it does not give you is an answer to *"where do
the parameters come from, and what happens when they change?"* — so every application grows
its own half of a configuration layer: environment variables read in three places, a
`ChatModel` built in a `@Bean` method, and a redeploy to change a temperature.

modelrack4j is that missing half, and nothing else:

- **Configuration is layered and merged**, so a defaults file, an environment file and a
  local override compose instead of replacing each other.
- **Validation is capability-aware**: enabling moderation on a provider that ships no
  moderation model fails when the config loads, not on the first request.
- **Reload is atomic across the whole snapshot.** Either every named bundle swaps or none
  does, so a multi-model flow never sees a new `SL` next to an old `SH`.

It deliberately does not do prompt templating, `AiServices`, tools, RAG, retries or
fallback — see [Out of scope](#out-of-scope).

---

## Quick start

### 1. Add the dependencies

Core knows no providers. Add core **plus** each provider module you actually configure —
each one registers itself through `ServiceLoader`.

```xml
<dependency>
  <groupId>io.github.maxtrezzi</groupId>
  <artifactId>modelrack4j-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.maxtrezzi</groupId>
  <artifactId>modelrack4j-provider-anthropic</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.maxtrezzi</groupId>
  <artifactId>modelrack4j-provider-openai</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Or import the BOM once and drop the versions:

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
```

Requires **Java 17+**. Built against **LangChain4j 1.19.0**.

### 2. Write the configuration

```hocon
# council.conf
llm {
  SL {
    description = "short, cheap, deterministic — the everyday answer"
    provider    = anthropic
    api-key     = ${ANTHROPIC_API_KEY}
    model-name  = "claude-sonnet-5"
    temperature = 0.2
    memory { type = message-window, max-messages = 20 }
  }

  SH {
    description = "the same model turned up, streamed for long answers"
    provider    = anthropic
    api-key     = ${ANTHROPIC_API_KEY}
    model-name  = "claude-sonnet-5"
    temperature = 0.9
    streaming   = true
  }

  CR {
    description = "the critic, and the only one that moderates"
    provider    = openai
    api-key     = ${OPENAI_API_KEY}
    model-name  = "gpt-5.1"
    temperature = 0.7
    moderation { enabled = true }
  }
}
```

Note `SL` and `SH`: **two names, one provider, one model, different parameters.** Registry
keys are configuration names, never provider names, which is what makes that work.

> **Use `${VAR}`, not `${?VAR}`, for secrets.** The mandatory form fails loudly at load time
> when the variable is unset. The optional form silently yields no value, and the failure
> resurfaces much later as an authentication error on the first request.

### 3. Use it

```java
try (LlmRegistry registry = LlmRegistry.builder()
        .configFiles(List.of(Path.of("council.conf")))
        .watch(true)
        .build()) {

    LlmBundle sl = registry.get("SL");
    String answer = sl.chatModel().chat("In one sentence: why merge before resolving?");

    registry.get("SH").streamingChatModel()
            .orElseThrow()
            .chat("Now at length.", handler);
}
```

`LlmBundle` is a record:

| Component | Type | Present when |
|---|---|---|
| `config()` | `LlmConfig` | always |
| `chatModel()` | `ChatModel` | always |
| `streamingChatModel()` | `Optional<StreamingChatModel>` | `streaming = true` |
| `moderationModel()` | `Optional<ModerationModel>` | `moderation.enabled = true` |
| `chatMemoryProvider()` | `Optional<ChatMemoryProvider>` | a `memory` block is configured |

A runnable version of exactly this scenario lives in
[`modelrack4j-examples`](modelrack4j-examples/src/main/java/io/github/maxtrezzi/modelrack4j/examples/ThreeModelCouncil.java),
alongside [`ConsoleChat`](modelrack4j-examples/src/main/java/io/github/maxtrezzi/modelrack4j/examples/ConsoleChat.java)
— an interactive menu of every configured model, with `/menu` to switch and `/exit` to
leave. Leave it running and edit the config file: the menu changes underneath you, which is
the fastest way to see what [hot reload](#hot-reload) actually does.

---

## ⚠️ Do not cache the bundle

**Ask the registry for the bundle at the point of use. Every time.**

```java
// ✅ correct — always the current bundle
class Council {
    private final LlmRegistry registry;

    String ask(String q) {
        return registry.get("SL").chatModel().chat(q);
    }
}

// ❌ wrong — frozen at construction, will never see a reload
class Council {
    private final ChatModel model;

    Council(LlmRegistry registry) {
        this.model = registry.get("SL").chatModel();   // captured once, forever
    }
}
```

This is the one mistake that silently disables hot reload. Nothing throws, nothing logs, the
reload genuinely happens — the caller is simply holding a bundle from a previous snapshot.
`registry.get(name)` is a read of a volatile field and a map lookup; it is cheap enough to
call per request, and it is the API's primary path. Listeners are secondary.

If you use a DI container, inject the **registry**, not a `ChatModel`.

---

## Configuration reference

Every named block lives under the `llm` root. Names are yours: `SL`, `CR`,
`summariser-eu`, anything.

| Key | Type | Default | Notes |
|---|---|---|---|
| `description` | string | *none* | a short human-readable note; see below |
| `provider` | string | *required* | must match a `ProviderFactory` on the classpath |
| `api-key` | string | *required* | use `${VAR}` |
| `model-name` | string | *required* | the provider's own model identifier |
| `temperature` | number | *provider's own* | 0.0–2.0; omitted means "don't set it" |
| `timeout` | duration | `60s` | HOCON durations: `30s`, `2m` |
| `streaming` | boolean | `false` | builds a `StreamingChatModel` alongside |
| `log-requests` | boolean | `false` | **prompts, and therefore user data, into your logs** |
| `log-responses` | boolean | `false` | same caveat |
| `memory.type` | string | *no memory* | `message-window` or `token-window` |
| `memory.max-messages` | int | — | required by `message-window` |
| `memory.max-tokens` | int | — | required by `token-window` |
| `memory.allow-remote-token-counting` | boolean | `false` | see [Memory](#memory) |
| `moderation.enabled` | boolean | `false` | builds a `ModerationModel` |

**`description` is for whoever did not write the file.** Names like `SL` and `CR` are
convenient to type and say nothing on their own, so a block can carry one line explaining
what it is for. Nothing in the library reads it — it is there for your own operators, your
own admin screens, and the console example's menu. Two rules worth knowing: a present but
**blank** description is rejected as a mistake, and a higher layer clears one set lower down
with `description = null`, which removes the key outright.

Because it is part of `LlmConfig`, editing a description alone counts as a change and
rebuilds that one bundle on reload. That is deliberate — see
[ADR-0032](docs/adr/0032-description-is-part-of-the-config-record.md).

**Model names are strings, and nothing here validates them.** `model-name` is passed
straight to the provider's builder. LangChain4j ships model-name *enums*, but they are a
convenience rather than a whitelist, and being compiled at release time they lag the
providers — a model released after LangChain4j 1.19.0 works perfectly well through this
library. The examples above use `claude-sonnet-5`, which no enum in the pinned jar lists,
alongside `gpt-5.1`, which one does. The trade is that a typo surfaces as the provider's own
error on the first request rather than at load time.

Omitting a key is meaningful: no `temperature` means the provider's default, no `memory`
block means no `ChatMemoryProvider` is built at all, no `moderation` block means no
moderation model. The defaults above are the ones in
[`modelrack4j-reference.conf`](modelrack4j-core/src/main/resources/modelrack4j-reference.conf).

### Layering

Files are listed **lowest precedence first**; the last one wins on conflict.

```java
LlmRegistry.builder()
        .configFiles(List.of(
                Path.of("/etc/myapp/defaults.conf"),   // baseline, in the image
                Path.of("/etc/myapp/prod.conf"),       // environment
                Path.of("./local.conf")))              // developer override
        .build();
```

```hocon
# defaults.conf — the key exists, but nothing can supply it here
llm {
  SL { provider = anthropic, model-name = "claude-sonnet-5", api-key = ${ANTHROPIC_API_KEY} }
}

# local.conf — a developer with no key, pointing at a local gateway
llm {
  SL { api-key = "not-a-secret", model-name = "local-model" }
}
```

That works, and the detail is load-bearing: all layers are **merged first and resolved
exactly once**. `${ANTHROPIC_API_KEY}` in the lower layer is never resolved, because the
higher layer replaced that key before resolution ran. Resolve each file as you parse it —
the obvious implementation — and this throws instead, because the substitution is evaluated
while the overriding layer is still invisible. It has its own regression suite.

Substitutions also see the merged result, so a lower layer may refer to a key only a higher
layer defines.

---

## Hot reload

```java
LlmRegistry registry = LlmRegistry.builder()
        .configFiles(files)
        .watch(true)                        // off by default
        .debounce(Duration.ofMillis(300))   // the default
        .build();

registry.onReload(change ->
        log.info("reloaded: updated={} added={} removed={}",
                change.updated(), change.added(), change.removed()));

registry.onReloadFailure(failure ->
        log.error("config rejected, previous snapshot still live", failure.cause()));
```

**What a reload guarantees:**

- **All or nothing, across the whole snapshot.** Every layer is re-parsed, re-validated and
  every changed bundle rebuilt in a staging area. If anything fails anywhere — a parse
  error, a validation failure, a provider builder throwing — *nothing* swaps, the previous
  snapshot stays live, and `onReloadFailure` fires once. A half-applied config never exists.
- **One callback per reload, never one per name.** Two callbacks would let an application
  observe a new `SL` beside an old `SH`, which is a correctness hazard for multi-model flows.
- **Unchanged blocks keep the same object.** The diff is record equality on the parsed
  config, so editing `SL` rebuilds `SL` and hands back the identical `SH` instance.
- **A callback means something changed.** A save that resolves to an identical snapshot
  swaps nothing and notifies nobody. It is not a heartbeat.
- **A listener that throws cannot break reloading.** Exceptions are caught and logged; the
  next reload still runs.

**Names come and go.** A name removed from the configuration is removed from the registry,
and `get()` on it then throws `UnknownConfigurationException`. Superseded bundles are **not**
closed — in-flight requests may still be holding them.

`LlmRegistry` is `AutoCloseable`; closing it stops the (daemon) watcher thread. With
`watch(false)` — the default — no thread is started at all.

### Logging

Core logs through **`slf4j-api`** and ships no binding — add your own (Logback, `slf4j-simple`,
whatever you already use), or SLF4J prints its no-provider notice and everything below is
discarded.

Two things are reported through the log and nowhere else, because they happen on the watcher
thread and have no caller to be thrown at:

- **A rejected reload**, at `WARN` from `io.github.maxtrezzi.modelrack4j.LlmRegistry`, with
  the cause. This is logged whether or not you registered `onReloadFailure`, which matters
  more than it sounds: a typo in a config file does not delay one reload, it makes *every*
  later edit to that file fail the same way, and without the log the only symptom is models
  that quietly stop reflecting the file.
- **A listener that threw**, at `ERROR`. The reload itself is unaffected.

Successful reloads are not logged. If you want that, register `onReload` and log what it
gives you.

### What the watcher handles

| Case | Handled |
|---|---|
| In-place rewrite | ✅ |
| Editor writing temp-file-then-rename | ✅ arrives as `ENTRY_CREATE`; the `.tmp` events are discarded |
| A burst of events from one save | ✅ collapsed by the debounce |
| Kubernetes ConfigMap symlink swap | ✅ the symlink's own directory is watched, and no event in that swap is named after your config file |
| A watched directory deleted and recreated | ✅ re-registered, retried once a second |

### Latency

Measured on **Linux** (inotify, Temurin 25), write → event observed, 20 samples:

| min | median | max |
|---|---|---|
| 0.37 ms | **0.50 ms** | 0.63 ms |

Plus the debounce, so a save is live roughly 300 ms later by default. Events for one logical
write arrive within ~2.5 ms, which is what the 300 ms default is sized against; lowering it
below the time your writer takes to finish produces reloads of half-written files, which are
rejected as failures rather than applied.

> **macOS is not measured.** The JDK's `WatchService` there is polling-based internally, so
> latency is expected to be substantially higher — on the order of seconds, not
> sub-millisecond. This project has no macOS machine, and rather than quote a figure from
> hearsay it states the gap: **if you run on macOS, measure it yourself.** Nothing about the
> design depends on the answer; only this paragraph does.

---

## Providers

Each provider is its own module. Core takes **no** provider artifact, ever, so an
application configuring only Anthropic never has OpenAI's dependencies on its classpath.

| Module | `provider =` | Chat | Streaming | Moderation | Token estimation |
|---|---|---|---|---|---|
| `modelrack4j-provider-openai` | `openai` | ✅ | ✅ | ✅ | **local** |
| `modelrack4j-provider-anthropic` | `anthropic` | ✅ | ✅ | ❌ | remote |
| `modelrack4j-provider-gemini` | `gemini` | ✅ | ✅ | ❌ | remote |
| `modelrack4j-provider-glm` | `glm` | ✅ | ✅ | ❌ | none |

Read out of the LangChain4j 1.19.0 artifacts, not from documentation. Gemini is the stable
`langchain4j-google-ai-gemini` module; GLM comes from `langchain4j-community-zhipu-ai`,
which is on the community release train.

Capabilities are enforced at load time. `moderation { enabled = true }` on Anthropic,
Gemini or GLM is a configuration error naming the block, not an empty `Optional` you
discover at runtime.

**Two provider notes worth knowing before you hit them:**

- **Moderation is ignored on the `AiServices` streaming path.** That is upstream LangChain4j
  behaviour ([#2779](https://github.com/langchain4j/langchain4j/issues/2779)), not something
  this library can fix — it only builds the objects. If you configure `streaming = true`
  together with `moderation.enabled = true` and then wire the bundle into `AiServices`,
  moderation will not run on that path.
- **GLM has no whole-call timeout.** Its client's `callTimeout` and `writeTimeout` are
  deprecated and marked for removal upstream, so the schema's single `timeout` is applied to
  connect and read only.

### Adding a provider

Implement `io.github.maxtrezzi.modelrack4j.spi.ProviderFactory` and register it in
`META-INF/services/io.github.maxtrezzi.modelrack4j.spi.ProviderFactory`. The interface is
seven methods, three of which return `Optional.empty()` when the capability does not exist.
The one that is not obvious is `tokenEstimation()`: it reports `ABSENT`, `LOCAL` or
`REMOTE`, not a boolean, because availability is not what varies — cost is.

---

## Memory

`message-window` counts messages and works everywhere.

`token-window` calls the provider's `TokenCountEstimator` on eviction, and what that costs
depends entirely on the provider:

| The provider counts | Behaviour |
|---|---|
| **locally** (OpenAI) | built, no ceremony |
| **remotely** (Anthropic, Gemini) | **rejected unless you opt in** with `allow-remote-token-counting = true` |
| **not at all** (GLM) | rejected outright; the opt-in flag does not apply |

The middle row is the point. On a remote counter, ordinary conversation turns make a billed,
rate-limited, network-dependent HTTP call inside what the application reasonably assumes is
in-memory bookkeeping. That is a decision, so it is opted into explicitly, and the rejection
message names the flag that permits it. On a local counter the flag is inert rather than an
error — one config layer commonly spans several providers.

---

## Out of scope

Deliberately, permanently:

- **`AiServices`, `@Tool` methods, RAG retrievers, guardrails.** Code-shaped, not
  config-shaped. This library builds the inputs you hand to `AiServices`; it does not wrap it.
- **Provider pools, fallback, retry, circuit breaking.** Resilience4j owns that.
- **Generic reloadable configuration.** Apache Commons Configuration owns that; the scope
  here is LangChain4j objects specifically.
- **`EmbeddingModel`** — not in v1.
- **A `ReloadableChatModel` hot-swap wrapper** — designed for, deferred to v2. Hot *reload*
  is here today; only the convenience wrapper that hides `registry.get()` is deferred.

---

## Building from source

```bash
mvn clean install                        # full build, installs 0.1.0-SNAPSHOT to ~/.m2
mvn -pl modelrack4j-core -am test        # core and its dependencies
mvn -Pintegration verify                 # provider tests against real APIs, keys from env
```

The default build is **offline and needs no API keys** — that is enforced by a CI job that
runs with the credential environment scrubbed. Integration tests are doubly guarded: they
only run under `-Pintegration`, and each one skips itself when its own key is absent, so
running the profile with one provider configured skips the rest instead of failing. They
cost real money. The keys they look for are `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`,
`GEMINI_API_KEY` and `ZHIPU_API_KEY`.

CI runs JDK 17 (the floor), 21 and 25.

---

## Documentation

- **[`docs/adr/`](docs/adr/README.md)** — why the library is shaped this way. Every design
  constraint above has an ADR behind it with the alternatives that were rejected.
- **[`docs/tasks/`](docs/tasks/README.md)** — what is done and what is next.
- **[CHANGELOG.md](CHANGELOG.md)** — Keep a Changelog, SemVer, 0.x until the API settles.

## License

[Apache License 2.0](LICENSE).

LangChain4j is a separate project under its own license; this library depends on it and is
not part of it.
