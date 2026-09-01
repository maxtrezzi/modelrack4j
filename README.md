# modelrack4j

Turn layered HOCON configuration into **named, ready-to-use bundles of LangChain4j
objects**, with validated hot reload.

Declare `SL`, `SH` and `CR` in a config file; ask the registry for each by name and get a
consistent `ChatModel` + `StreamingChatModel` + `ModerationModel` + `ChatMemoryProvider`
per name. Edit the file and the registry picks the change up atomically, validated, without
a restart.

```hocon
# llm.conf
llm {
  SL { provider = anthropic, api-key = ${ANTHROPIC_API_KEY}, model-name = "claude-sonnet-5" }
  CR { provider = openai,    api-key = ${OPENAI_API_KEY},    model-name = "gpt-5.1" }
}
```

```java
LlmRegistry registry = LlmRegistry.builder().configFiles(List.of(Path.of("llm.conf"))).build();
String answer = registry.get("SL").chatModel().chat("Why is the sky blue?");
```

That is the whole idea. [Quick start](#quick-start) has the dependencies and the full schema.

> **Unofficial and independent.** modelrack4j is not affiliated with, endorsed by, or part
> of the LangChain4j project. It depends on LangChain4j; it does not speak for it. That is
> also why no artifact here uses the `langchain4j-` prefix.

📖 **[The manual](docs/manual/README.md)** — a [tutorial](docs/manual/part-1-tutorial.md) that
starts from nothing, and a [reference](docs/manual/part-2-reference.md) for everything else.

**Status: pre-release, `0.1.0-SNAPSHOT`.** Not published to Maven Central yet — build and
install it locally (see [Building](#building-from-source)). The API is 0.x and may still
change; see [CHANGELOG.md](CHANGELOG.md).

---

## Why

LangChain4j is already provider-agnostic where it counts: `ChatModel` is an interface and
every provider implements it, so the code you write against it does not care who answers.

What is *not* agnostic is the act of **choosing**. Picking a provider, naming a model,
setting a temperature and a timeout is a constructor call — which means it is code:

```java
ChatModel model = AnthropicChatModel.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .modelName("claude-sonnet-4-6")
        .temperature(0.2)
        .timeout(Duration.ofSeconds(60))
        .build();
```

Changing the provider means editing that, recompiling and redeploying. So does raising a
temperature by 0.1. The abstraction is real, but the decision it was supposed to free you
from is tied to the build.

**modelrack4j moves that decision into a file.** Three things follow, and they are the whole
library:

1. **The provider becomes configuration.** `provider = anthropic` → `provider = openai` is an
   edit, not a release. Nothing in your code selects a provider, and nothing needs to know
   which one answered.

2. **Several setups of the same model cost nothing.** `SL` and `SH` below are one provider and
   one model, one bounded by memory and the other streamed. In code that is two near-identical
   builder blocks that drift apart over time; here it is two named blocks, and adding a third
   is four lines in a file.

3. **Configuration changes while the process runs.** Edit the file and the registry rebuilds
   what changed and swaps it — validated, and in one step across every configured model, so a
   half-applied configuration never exists. A flow that uses `SL` and `SH` together gets both
   from the same version of the file by asking for a [snapshot](#hot-reload). If the new file
   is broken, nothing swaps and the previous configuration keeps serving.

The fourth thing is what makes the first three safe to rely on: **mistakes fail when the file
loads, not at the first request.** Providers differ in what they can actually do — moderation
is OpenAI-only among the four here, and token counting is local, remote or absent depending
on the provider — so the configuration is validated against the provider's real capabilities.
Enabling moderation on Anthropic is a startup error naming the block, not an empty `Optional`
you discover in production.

It deliberately does not do prompt templating, `AiServices`, tools, RAG, retries or
fallback — see [Out of scope](#out-of-scope).

> That is the case against building this yourself on top of plain LangChain4j. If you are on
> Spring Boot or Quarkus, the comparison is a different one — read on.

---

## Should you use this?

Often the answer is no, and it is cheaper to find that out here than three days into the
project.

**If you are on Spring Boot or Quarkus, start with their LangChain4j starters.** They already
configure models from properties at startup, and for most applications that is the whole job.
Neither needs this library:

| | Spring Boot starter | Quarkus LangChain4j | modelrack4j |
|---|---|---|---|
| Models configured from properties at startup | ✅ | ✅ | ✅ |
| Several models, keyed by a name you choose | ❌ one namespace per provider, wired by bean name | ✅ `quarkus.langchain4j.openai.my-model.…` | ✅ |
| **Two** configurations of the **same** provider, from configuration alone | ❌ needs a hand-written `@Bean` | ✅ | ✅ |
| Configuration reloaded **without a restart** | ❌ | ❌ | ✅ |
| Requires a framework | Spring | Quarkus | ❌ plain Java |

So there are exactly two reasons to reach for this instead:

1. **You are not on one of those frameworks**, or you do not want your model configuration
   coupled to the one you are on. This is plain Java with no container, no annotations and no
   classpath scanning.
2. **You need configuration to change while the process runs.** Neither starter documents a
   way to do that, and it is the thing this library is built around — validated, atomic across
   every configured model at once, and with the previous configuration left running if the new
   one does not parse.

If neither applies, use the starter for your framework. It will be less code than this.

> Checked against the [Spring Boot](https://docs.langchain4j.dev/tutorials/spring-boot-integration/)
> and [Quarkus](https://docs.quarkiverse.io/quarkus-langchain4j/dev/models.html) documentation
> in August 2026, for LangChain4j 1.19.0. Both projects move quickly — if one of them has
> added reload since, this table is out of date and the comparison above is the part to
> distrust first.

---

## Quick start

### 1. Add the dependencies

Core knows no providers. Add core **plus** each provider module you actually configure —
each one registers itself through `ServiceLoader`.

> **Build it first.** These coordinates are not on Maven Central yet, so copying the snippet
> into a project resolves nothing until you have run `mvn clean install` in a clone of this
> repository — see [Building from source](#building-from-source). The `-SNAPSHOT` suffix is
> what tells you that: it resolves from your local `~/.m2`, not from Central.

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
# llm.conf
llm {
  SL {
    description = "short, cheap — the everyday answer, twenty turns of memory"
    provider    = anthropic
    api-key     = ${ANTHROPIC_API_KEY}
    model-name  = "claude-sonnet-5"
    memory { type = message-window, max-messages = 20 }
  }

  SH {
    description = "the same model, streamed for long answers"
    provider    = anthropic
    api-key     = ${ANTHROPIC_API_KEY}
    model-name  = "claude-sonnet-5"
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
keys are configuration names, never provider names, which is what makes that work. Neither
sets `temperature`: `claude-sonnet-5`'s adaptive thinking controls its own sampling, and the
API rejects a non-default value with a 400. The `gpt-5.1` block above still accepts one, and
so does
`claude-sonnet-4-6` — which is why the Java example in [Why](#why) uses that model to show a
temperature fixed in a builder call.

> **Use `${VAR}`, not `${?VAR}`, for secrets.** The mandatory form fails loudly at load time
> when the variable is unset. The optional form silently yields no value, and the failure
> resurfaces much later as an authentication error on the first request.

### 3. Use it

```java
try (LlmRegistry registry = LlmRegistry.builder()
        .configFiles(List.of(Path.of("llm.conf")))
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

### Runnable examples

Five, in [`modelrack4j-examples`](modelrack4j-examples/src/main/java/io/github/maxtrezzi/modelrack4j/examples).
Each one demonstrates a single claim from [Why](#why) rather than the library in general:

| Example | Shows | Cost |
|---|---|---|
| [`AtomicSnapshot`](modelrack4j-examples/src/main/java/io/github/maxtrezzi/modelrack4j/examples/AtomicSnapshot.java) | A single save changes two models at once, while four threads keep reading both; reading through `snapshot()` never catches a mix of old and new, reading through two `get()` calls can, though a single run often catches none | **free, no API key** |
| [`DatabaseSource`](modelrack4j-examples/src/main/java/io/github/maxtrezzi/modelrack4j/examples/DatabaseSource.java) | Configuration held in memory instead of a file, standing in for a database row, driven by the application: all four answers `reload()` can give, then the same rejected change through `store()`, which refuses it before the row is written | **free, no API key** |
| [`ProviderSwap`](modelrack4j-examples/src/main/java/io/github/maxtrezzi/modelrack4j/examples/ProviderSwap.java) | The same call site answered by Anthropic, then by OpenAI, after a file edit | two requests |
| [`ConsoleChat`](modelrack4j-examples/src/main/java/io/github/maxtrezzi/modelrack4j/examples/ConsoleChat.java) | An interactive menu of every configured model; edit the file while it runs and the menu changes | a conversation |
| [`ThreeModelCouncil`](modelrack4j-examples/src/main/java/io/github/maxtrezzi/modelrack4j/examples/ThreeModelCouncil.java) | The scenario above: three models, one question, no provider branch in the code | three requests |

Start with `AtomicSnapshot` if you want to see the least obvious guarantee at no cost:

```bash
./run-atomic.sh
```

One script per example — `run-atomic.sh`, `run-database.sh`, `run-swap.sh`, `run-chat.sh`,
`run-council.sh` — each with a `--help` that says what it shows and what it costs. They
install the project first if they have to, because `exec:java` resolves `modelrack4j-core`
from `~/.m2` rather than from the reactor. There are no `.bat` counterparts: on Windows,
run the command `--help` prints.

```bash
mvn install
mvn -q -pl modelrack4j-examples exec:java \
    -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.AtomicSnapshot
```

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
`registry.get(name)` is a read of a volatile field, one small wrapper object, and a map
lookup; it is cheap enough to call per request, and it is the API's primary path. Listeners
are secondary.

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
**blank** description is rejected as a mistake, and a higher layer can clear a description
set in a lower layer with `description = null`, which removes the key outright.

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
higher layer replaced that key before resolution ran. If instead you resolve each file as
you parse it — the obvious implementation — this fails, because the substitution is
evaluated while the overriding layer is still invisible. It has its own regression suite.

Substitutions also see the merged result, so a lower layer may refer to a key only a higher
layer defines.

---

### Layers that are not files

A layer can also be a row in a database or a value from a configuration service. Give the
registry `sources(...)` instead of `configFiles(...)`, and tell it when to re-read:

```java
ConfigSource row = new ConfigSource() {
    public String id()   { return "llm_config#42"; }   // a label for error messages
    public String text() { return jdbc.readConfigText(42); }
};

LlmRegistry registry = LlmRegistry.builder()
        .sources(List.of(ConfigSource.ofFile(basePath), row))
        .build();

jdbc.updateConfigText(42, newText);
registry.reload();   // nothing watches a database row, so you say when
```

Files and other sources mix in one list, in the same order. `reload()` returns what changed,
or nothing when the configuration turns out to be the same. See the
[reference](docs/manual/part-2-reference.md#configuration-that-is-not-a-file).

### Writing a layer back

The two lines above save first and validate second, so an invalid text is already in the row
when the reload rejects it, and the next start fails. `store` does it the other way round:

```java
registry.store(row, newText);   // validated, applied, and only then saved
```

Nothing is written and nothing changes if the text would not load; if saving itself fails,
the previous configuration comes back and the call throws. A store raises no reload event —
the caller already knows what changed, and is given it as the return value. The target has to
be a `WritableConfigSource`, which is the interface above plus a `write(String)` method;
`ConfigSource.ofWritableFile(path)` gives you one for a file. Where more than one writer is
possible, `storeIfUnchanged(layer, base, newText)` refuses instead of erasing somebody else's
change. See [Storing a layer back](docs/manual/part-2-reference.md#storing-a-layer-back).

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

- **Ask for consistency when you need it.** `registry.get(name)` reads the live
  configuration on every call — that is what makes reload work, and it means **a reload can
  land between two consecutive calls**, so they return models built from two different
  generations of the configuration. Rare (measured at roughly two per million read pairs
  under a reload every few milliseconds, on an AMD Ryzen 7 7840HS running Temurin 25) but
  reproducible, and a correctness hazard wherever several models must agree. Where they must, take a snapshot:

  ```java
  LlmSnapshot models = registry.snapshot();   // one read of the current generation
  var fast = models.get("SL");
  var deep = models.get("SH");                // guaranteed same generation as fast
  ```

  A snapshot never updates — take one per unit of work, not one at startup, or you have
  re-created the caching trap above. See
  [ADR-0038](docs/adr/0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md).

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
thread, where there is no caller to throw them to:

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

Measured on **one Linux machine** — AMD Ryzen 7 7840HS, ext4 on NVMe, Pop!_OS 24.04
(kernel 7.0.11), Temurin 25 — over inotify, write → event observed, 20 samples:

| min | median | max |
|---|---|---|
| 0.37 ms | **0.50 ms** | 0.63 ms |

One machine is not a benchmark. Read it as *the notification is push-based and costs well
under a millisecond here*, not as a number your hardware will repeat — a slower disk or a
loaded machine moves it, and nothing in the design depends on where it lands.

What you actually wait for is the debounce, so a save is live roughly 300 ms later by
default. Events for one logical write arrived within ~2.5 ms on the same machine, which is
the burst the 300 ms default is chosen to cover; lowering it below the time your writer takes
to finish produces reloads of half-written files, which are rejected as failures rather than
applied.

> **macOS is not measured.** The JDK's `WatchService` there is polling-based internally, so
> latency is expected to be substantially higher — on the order of seconds, not
> sub-millisecond. This project has no macOS machine, and rather than quote an unverified
> figure it says so openly: **if you run on macOS, measure it yourself.** Nothing about the
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
which is released on the community cycle, separately from the stable modules.

Capabilities are enforced at load time. `moderation { enabled = true }` on Anthropic,
Gemini or GLM is a configuration error naming the block, not an empty `Optional` you
discover at runtime.

**Three provider notes worth knowing before you hit them:**

- **Moderation is ignored on the `AiServices` streaming path.** That is upstream LangChain4j
  behaviour ([#2779](https://github.com/langchain4j/langchain4j/issues/2779)), not something
  this library can fix — it only builds the objects. If you configure `streaming = true`
  together with `moderation.enabled = true` and then wire the bundle into `AiServices`,
  moderation will not run on that path.
- **GLM has no whole-call timeout.** Its client's `callTimeout` and `writeTimeout` are
  deprecated and marked for removal upstream, so the schema's single `timeout` is applied to
  connect and read only.
- **Swapping a provider in config does not swap your error handling.** What a *failing* call
  throws is the provider's, not this library's, and the types differ: an out-of-credit account
  is `RateLimitException` on OpenAI and `ZhipuAiException` on GLM — the same condition, two
  types. Catch `dev.langchain4j.exception.LangChain4jException` for handling that survives a
  swap; anything finer is provider-specific. The guarantee covers construction, not invocation
  ([ADR-0033](docs/adr/0033-provider-exceptions-pass-through-untranslated.md)).

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
| **locally** (OpenAI) | built, with no extra configuration |
| **remotely** (Anthropic, Gemini) | **rejected unless you opt in** with `allow-remote-token-counting = true` |
| **not at all** (GLM) | rejected outright; the opt-in flag does not apply |

The middle row is the point. On a remote counter, ordinary conversation turns make a billed,
rate-limited, network-dependent HTTP call inside what the application reasonably assumes is
in-memory bookkeeping. That is a decision, so it is opted into explicitly, and the rejection
message names the flag that permits it. On a local counter the flag has no effect rather
than being an error — one config layer commonly spans several providers.

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

- **[`docs/manual/`](docs/manual/README.md)** — the manual: a tutorial built on the runnable
  examples, then a reference for the schema, the API, reload semantics and troubleshooting.
- **[`docs/adr/`](docs/adr/README.md)** — why the library is shaped this way. Every design
  constraint above has an ADR behind it with the alternatives that were rejected.
- **[`docs/tasks/`](docs/tasks/README.md)** — what is done and what is next.
- **[CHANGELOG.md](CHANGELOG.md)** — Keep a Changelog, SemVer, 0.x until the API settles.

## License

[Apache License 2.0](LICENSE), with a [`NOTICE`](NOTICE) file. Both are copied into
`META-INF/` of every published jar, so they reach you whether you clone the repository or
just take the artifact.

If you redistribute this library or a derivative of it, §4(d) of the License asks you to
carry the `NOTICE`'s attribution along. It is four lines, and that is deliberate — see
[ADR-0035](docs/adr/0035-ship-a-notice-file-for-attribution.md).

LangChain4j is a separate project under its own license; this library depends on it and is
not part of it.
