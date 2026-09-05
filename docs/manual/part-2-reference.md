# Part 2 — Reference

What every key means, what every method promises, and what the library does when things go
wrong. [Part 1](part-1-tutorial.md) is the way in; this is the page you come back to.

**Contents**

| | |
|---|---|
| [Concepts](#concepts) | five words used precisely |
| [Dependencies](#dependencies) | what to put in your POM |
| [Examples](#examples) | five runnable programs, one claim each |
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
| **Layer** | One piece of configuration text, given as a `ConfigSource`. Usually a file, but it can be a row in a database or anything else that produces text. Layers merge into one snapshot; they do not each produce their own. |
| **Notifier** | What tells the registry that a layer changed, as a `ChangeNotifier`. Files get one built in; a layer nothing can watch has none, and the application calls `reload()` instead. |
| **Provider** | A LangChain4j integration, wrapped in a `ProviderFactory` and discovered on the classpath. Never a registry key. |

---

## Dependencies

**Java 17 or newer.** Built and tested on 17, 21 and 25.

**On Maven Central** since `0.1.0`. Import the BOM once, then declare artifacts without
versions:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.maxtrezzi</groupId>
      <artifactId>modelrack4j-bom</artifactId>
      <version>0.1.0</version>
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
| `DatabaseSource` | [Configuration that is not a file](#configuration-that-is-not-a-file): a layer held in memory, standing in for a database row, driven entirely by the application. It shows all four answers `reload()` can give — a name added, a name updated, nothing changed, and a rejected reload that leaves the previous configuration live — and then the same rejected change offered through [`store()`](#storing-a-layer-back) instead, which refuses it before the row is written rather than after. | **nothing** — sends no request |
| `ProviderSwap` | The provider as configuration: the same method, called twice around a file edit, answered by `AnthropicChatModel` and then `OpenAiChatModel`. The method names no provider and has no branch. | `ANTHROPIC_API_KEY` + `OPENAI_API_KEY`, two requests |
| `ConsoleChat` | Everything interactively: a menu of configured models, streaming where configured, moderation on input where configured, memory across turns, and reload while you watch. `/tools` switches the answering path to an `AiServices` proxy with a `@Tool` method — see [What you still write yourself](#what-you-still-write-yourself) — built on the bundle that turn fetched. | `ANTHROPIC_API_KEY` + `OPENAI_API_KEY` with the shipped `examples.conf`, which configures both providers; a configuration of your own can need one |
| `ThreeModelCouncil` | The multi-model scenario: three names, the questions you type, capabilities read from the bundle, all read from one `snapshot()` per round so the members answer under the same configuration. A model that fails does not end the round: its exception is printed beside the others — a different type per provider, as [Exceptions](#exceptions) describes — and the round says how many answered. | two provider keys, three requests per question |

Run them with `exec:java` after `mvn install` — the plugin resolves `modelrack4j-core` from
`~/.m2` rather than from the reactor, so a stale local install is the usual cause of a
`NoSuchMethodError` here:

```bash
mvn install
mvn -q -pl modelrack4j-examples exec:java \
    -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.AtomicSnapshot
```

`./run-atomic.sh` from the repository root does the same thing, and installs first if it has
to. There is one script per example — `run-atomic.sh`, `run-database.sh`, `run-swap.sh`,
`run-chat.sh`, `run-council.sh` — and each `--help` gives what that example shows, what it
costs, which keys it needs and the plain `mvn` command to use on Windows, since there are no
`.bat` counterparts.

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
| `api-key` | string | *required* | Use `${VAR}`. Never blank. On GLM it must have the form `id.secret` — see [Providers](#providers). |
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
1.20.0 works fine here. The cost is that a typo surfaces as the provider's own error on the
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

A layer that does not exist, or cannot be read, is a `ConfigAccessException` naming the path.
A layer that is there but malformed is a `ConfigValidationException`. The two are separate
types because the answer is different: the first has to be retried or fixed on the machine,
the second has to be corrected in the text.

There is no "skip what is missing" mode. The reason: an operator expects every listed file to
be read, and a file that was silently skipped is a worse outcome than a start that fails
immediately.

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
| `configFiles(List<Path>)` | The layers, as files, lowest precedence first. At least one. |
| `sources(List<ConfigSource>)` | The layers, from anywhere: see *[Configuration that is not a file](#configuration-that-is-not-a-file)*. Replaces `configFiles`; the last of the two calls wins. |
| `watch(boolean)` | Off by default. On, the registry starts one daemon thread and watches the layers that are files, whichever of the two methods above supplied them. Layers that are not files are ignored; at least one must be a file. |
| `notifier(ChangeNotifier)` | Something of your own that tells the registry when the configuration changed. Cannot be combined with `watch(true)`. |
| `debounce(Duration)` | How long the files must be quiet before a reload runs. Must be positive. |
| `build()` | Parses, validates and builds everything, then starts the notifier if there is one. Throws `ConfigValidationException` if no layer was given, two layers share an id, any block is invalid, or `watch(true)` was set and no layer is a file; `ConfigAccessException` if a layer cannot be read; `UncheckedIOException` if a directory cannot be watched. |

`build()` is all-or-nothing: one bad block means no registry, not a registry missing one name.

### Using it

| Method | Returns |
|---|---|
| `get(String name)` | The current bundle. Throws `UnknownConfigurationException` if the name is not configured *now*. |
| `snapshot()` | The current generation, held still, as an `LlmSnapshot`. Every lookup on it belongs to that one generation. |
| `names()` | The configured names, sorted. |
| `sources()` | The layers the registry was built from, lowest precedence first, unmodifiable. The list never changes: a reload re-reads the same layers. Use it to find the layer to write instead of keeping the reference beside the registry — but write it through [`store()`](#storing-a-layer-back), never through its own `write(String)`. |
| `onReload(Consumer<ReloadChange>)` | Registers a listener for successful reloads. |
| `onReloadFailure(Consumer<ReloadFailure>)` | Registers a listener for rejected ones. |
| `reload()` | Re-reads every layer now. Returns `Optional<ReloadChange>` — empty when nothing changed. Throws if the new configuration is rejected; the old one stays live. |
| `close()` | Stops the notifier. Idempotent. A closed registry keeps serving its last snapshot. |

`get()` is a volatile read, one small wrapper object, and a map lookup. It is meant to be
called per request — that is the primary API, and listeners are secondary.

### Configuration that is not a file

A layer does not have to be a file. It can be a row in a database, a value from a
configuration service, or text your program builds. A layer is a `ConfigSource`, which has
two methods:

```java
public interface ConfigSource {
    String id();      // a label for error messages
    String text();    // the HOCON text of this layer
}
```

```java
ConfigSource row = new ConfigSource() {
    public String id()   { return "llm_config#42"; }
    public String text() { return jdbc.readConfigText(42); }   // your query
};

LlmRegistry registry = LlmRegistry.builder()
        .sources(List.of(ConfigSource.ofFile(basePath), row))   // base file, then the row
        .build();
```

Files and other sources mix freely, in the same order rule: lowest precedence first.

**`include` works in a file layer and not in the others.** HOCON's
`include "other.conf"` looks for the file next to the one that contains the line, so it keeps
working for `configFiles(...)` and for `ConfigSource.ofFile(...)`. A layer that is not a file
has no directory of its own, so an include in it is looked up on the classpath instead —
and a HOCON include that finds nothing is **not an error**, it simply adds nothing. If you
store configuration in a database, assemble the text yourself rather than relying on
`include`.

Three things to know about writing one.

**`text()` is called again on every reload.** A source that runs its query each time works
correctly. A source that reads its text once and keeps it will never report a change.

**`id()` is a label, not an address.** The library never resolves it — it prints it, wherever
it has to say which layer it is talking about: a parse error, a layer it could not read,
`ReloadFailure`, the refusal when `watch(true)` finds no file among the layers, the refusal of
a `store` into a layer that is not this registry's, and `StaleLayerException.layerId()`. Give
it something a person reading a log can recognise. Do not put a secret in it, because it is
written to the log. Two sources of one registry must have different ids, and `build()` refuses
them if they do not.

**A layer that is not a file is not watched.** The library can watch files, because the
operating system tells it when a file changes. It cannot know when a database row changes.

`watch(true)` is still allowed in the example above, and it watches `basePath`. It watches
every layer that is a file and ignores the others, and it only refuses to start when no layer
is a file at all. So a mixed configuration is watched over its file half, and you have two
ways to tell the registry about the rest.

### Asking for a reload

Call `reload()` when your program knows the configuration changed:

```java
jdbc.updateConfigText(42, newText);
Optional<ReloadChange> change = registry.reload();
```

It returns what changed, or an empty `Optional` when the new configuration turns out to be
the same as the one already in effect. If the new configuration is invalid it throws, and
the previous configuration stays live in full — exactly as it does for a file that was saved
with a mistake in it.

You can call it from any thread and at any time. Reloads run one at a time, so a call may
wait for a reload that is already running. Do **not** call it from inside a reload listener:
listeners run inside the reload itself.

The other way is to give the registry something that knows when the configuration changed:

```java
public interface ChangeNotifier extends AutoCloseable {
    void start(Runnable onChange);
    void close();
}
```

The registry calls `start` once, and your code calls `onChange` whenever the configuration
may have changed. A call that turns out to change nothing costs one re-read and publishes
nothing, so call rather than stay silent when you are not sure. `close()` is called by
`LlmRegistry.close()`.

**`close()` must not wait forever.** It can be called while a reload is running, and a thread
your notifier waits for may itself be waiting for that reload to end. So if `close()` waits
for a thread, give the wait a timeout, the way `FileChangeNotifier` does. A plain `join()`
with no timeout makes the two threads wait for each other, and neither one ever returns.

Use it for a mechanism the library does not provide — a database `LISTEN`/`NOTIFY`, a
Kubernetes informer, a message from a queue. For files, `watch(true)` already builds the
right notifier, and the two cannot both be set.

The one the library ships is `FileChangeNotifier`, described under
[The watcher](#the-watcher). `watch(true)` builds it for you, and that is the usual way to get
one — including for layers passed to `sources(...)`.

Build it yourself only for a file the library cannot recognise as one: your own `ConfigSource`
that reads a file. `watch(true)` sees the file layers this library made,
`ConfigSource.ofFile(...)` and `ConfigSource.ofWritableFile(...)`, and it cannot see inside
an implementation you wrote.

```java
ConfigSource own = new MyFileSource(ownPath);   // your class, reading a file

LlmRegistry.builder()
        .sources(List.of(ConfigSource.ofFile(basePath), own))
        .notifier(FileChangeNotifier.of(List.of(basePath, ownPath), Duration.ofMillis(300)))
        .build();
```

Keep the two lists in step. Nothing compares the paths you pass to `sources(...)` with the
paths you pass to `FileChangeNotifier.of(...)`, so a path you forget in the second list is a
layer that is never reloaded, with no error and no log line.

| Method | Contract |
|---|---|
| `FileChangeNotifier.of(List<Path>, Duration)` | A notifier for those files, not yet started. An empty list throws `ConfigValidationException`; a duration that is zero or negative throws `IllegalArgumentException`, as `debounce(...)` does for the same value. |
| `start(Runnable)` | Called once by `build()`. Starting twice, or starting one that was closed, throws `IllegalStateException` — a closed notifier is spent. |
| `close()` | Called by `LlmRegistry.close()`. Stops the daemon thread, waiting up to five seconds for a reload it had already started. |

The registry owns whatever notifier it was given, from a successful `build()` until
`close()`.

### Storing a layer back

An application that lets its users change the configuration has to save that change
somewhere. Saving it yourself and then calling `reload()` works, but the two steps are in the
wrong order: if the new text is invalid, the reload rejects it and the layer is left holding
text that does not load. The next start then fails.

`store` puts them in the right order:

```java
WritableConfigSource userLayer = ConfigSource.ofWritableFile(Path.of("user.conf"));

LlmRegistry registry = LlmRegistry.builder()
        .sources(List.of(ConfigSource.ofFile(basePath), userLayer))
        .watch(true)                                 // both layers are files, so both are watched
        .build();

Optional<ReloadChange> change = registry.store(userLayer, newText);
```

`watch(true)` and `store` work together: the registry saves the change your application made,
and still picks up an edit someone makes in an editor.

It validates the whole configuration against the new text, applies it, and only then stores
it. A text that would not load is refused, with nothing written and nothing changed. If
storing itself fails — a directory that cannot be written, a database that is down — the
previous configuration comes back and the call throws.

**For a file layer, it is the directory that has to be writable, not the file.** The new text
is written to a temporary file beside the target and then moved onto it, so that no reader
ever sees the layer half written. A file that is itself read-only is therefore still stored;
a writable file in a read-only directory is not. Nothing checks this when the registry is
built, so a deployment that mounts the configuration read-only finds out at the first
`store()`, as a `ConfigAccessException`.

**Only a `WritableConfigSource` can be the target.** Whether a layer can be read says nothing
about whether it can be written, so a base layer you ship stays read-only because you never
made it one. `ConfigSource.ofWritableFile(Path)` gives you one for a file; implement the
interface for anything else.

**The text replaces the layer's whole content.** To change one value, start from `text()` and
give back the result. Write only what belongs to this layer. If you copy in the values it
inherits from the layers below, those values are frozen there, and a later change to a lower
layer stops reaching your application — silently, because the result is still valid.

**The text is stored as you give it, and is never resolved.** A `${VAR}` in it stays a
`${VAR}`, so no resolved secret is written into the layer.

**A store raises no reload event.** The caller made the change and gets it back as the return
value, so no listener runs. A file watcher that wakes up afterwards re-reads, finds what is
already live, and publishes nothing.

| Method | Contract |
|---|---|
| `store(WritableConfigSource, String)` | Validates, applies, stores. Returns what changed, or empty when the new text means what was already live — a text that only reformats is stored, and reported as no change. A text that does not validate throws `ConfigValidationException`; a layer that cannot be written throws `ConfigAccessException`, and both leave the previous configuration live. |
| `storeIfUnchanged(WritableConfigSource, String, String)` | The same, but only while the layer still holds the text passed as `expected`. Otherwise throws `StaleLayerException`, which carries the text the layer holds now. |
| `ConfigSource.ofWritableFile(Path)` | A file layer that can also be written. It writes through a temporary file beside the file it will replace, so a reader never sees half a write — which is why the **directory** is what needs write permission, not the file. It follows a symbolic link instead of replacing it, and it keeps the permissions the file already had. |
| `WritableConfigSource.write(String)` | What the library calls to store the text. Implement it for a layer of your own: make it one statement, make sure it stores nothing at all if it throws, and throw `ConfigAccessException` when the medium fails. |

#### More than one writer

A store is atomic against reloads and against other stores. It does not hold your *read* and
your *store* together: two threads that both call `text()` and then `store(...)` lose one of
the two changes. Where that can happen, pass the text you started from:

```java
String base = userLayer.text();
while (true) {
    try {
        registry.storeIfUnchanged(userLayer, base, withMyChangeApplied(base));
        break;
    } catch (StaleLayerException stale) {
        base = stale.current();   // somebody else wrote it: apply your change to that
    }
}
```

The comparison is on the text, character for character, not on what it means. A layer that
somebody reformatted or added a comment to is a layer that moved, and the store is refused.
That is the point of the check: a comment is a change a person made on purpose.

A trailing newline is one of those characters. `text()` gives the layer's content back as it is,
final newline included, so anything that drops it produces an `expected` that no longer matches —
a shell `expected=$(cat layer.conf)` drops it, and the refusal then looks like a bug in the check.
Pass the text on as you received it.

`StaleLayerException` carries two things: `current()`, the text the layer holds now, and
`layerId()`, the `id()` of the layer that moved — useful when one retry loop serves more than
one layer.

**When the other writer is not in your process.** Behind an HTTP `PUT` the two writers are two
clients, and the condition arrives as a header. The library needs nothing new for this: the
server that answers the request has already read the layer, so it can build the token itself.

```java
// GET: hand the client the text, and a token over that text
String text = userLayer.text();
response.header("ETag", etagOf(text));       // any stable digest of those exact bytes
response.body(text);

// PUT: apply the change only if the layer still holds what that client was given
String current = userLayer.text();
if (!etagOf(current).equals(request.header("If-Match"))) {
    return status(412);                      // somebody else wrote it first
}
try {
    registry.storeIfUnchanged(userLayer, current, request.body());
    return status(204);
} catch (StaleLayerException stale) {
    return status(412);                      // it moved between the check above and the write
}
```

The client sends a short token; the document itself never travels in a header. The `request`,
`response` and `status` calls stand for whatever your web framework provides — the two library
calls are `text()` and `storeIfUnchanged`.

**The `catch` is not a repeat of the `if`.** The header check compares against a read that is
already in the past. `storeIfUnchanged` reads the layer again inside the registry's lock and
compares it there, so it closes the gap between your check and your write — which no header
comparison can close. A store from another writer placed between those two lines passes the
`if` and is still refused.

`etagOf` is yours, and the library never sees it. Any stable function of the exact bytes will
do. Use the same string for the token and for `expected`: if you strip the trailing newline
before hashing but pass the original text to `storeIfUnchanged`, the two stop describing the
same layer.

#### Two limits

**`include` in a layer you store.** It keeps working: the include is resolved during
validation next to the file itself, exactly as it will be resolved afterwards. The one case
that is refused is a layer reached through a symbolic link into another directory, because
the include would be checked in one directory and read in another.

**A dropped `include` is not caught.** The library cannot tell a deliberate removal from an
accidental one. If your new text leaves out an `include` the configuration did not strictly
need, the store succeeds and the values it brought are gone. Validation is the only guard
here, and it only asks whether the result loads.

### One lookup, or several that must agree

`get()` reads the live configuration on every call. That is what makes a reload visible, and
it means **a reload can land between two consecutive calls**, so the two calls return bundles
built from two different generations of the configuration. It is rare — measured at roughly
two per million pairs of reads while a reload ran every few milliseconds, on an AMD Ryzen 7
7840HS running Temurin 25 — but it is reproducible, and where several models have to agree it is a correctness problem rather than
a cosmetic one.

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

record ReloadFailure(List<ConfigSource> sources, Exception cause) { }
```

`LlmConfig` validates in its compact constructor, so an instance that exists is valid.
`MemoryConfig` is sealed with a record per variant rather than one record carrying unused
fields — `max-messages` and `max-tokens` cannot both be set, because no type has both.

**`LlmConfig.toString()` prints `apiKey=***`.** `apiKey()` holds the key after substitution —
the real credential, not the `${VAR}` your file was written with — so the generated
`toString()` of a record would put it in any log line that prints a config or a bundle. Only
`toString()` is redacted: `equals` and `hashCode` still compare the key, because a rotated
credential has to count as a changed configuration and trigger a reload. If you log
configuration yourself, print the fields you want rather than the record.

Value equality on `LlmConfig` is load-bearing: it is how a reload decides what changed. Every
component participates, including `description`.

### Exceptions

| Exception | When |
|---|---|
| `ConfigValidationException` | A block is malformed, a value is out of range, a substitution is unresolved, or a provider rejects its configuration. Always something about the text. Unchecked. |
| `ConfigAccessException` | A layer cannot be reached: a file that is missing or unreadable, a directory that cannot be written, a full disk, a database that is down. Nothing is wrong with the configuration — it could not be read or stored. **Not** a subclass of `ConfigValidationException`. Unchecked. |
| `UnknownConfigurationException` | `get()` on a name that is not in the current snapshot. |
| `UncheckedIOException` | Watching was requested and a directory cannot be watched. |
| `StaleLayerException` | `storeIfUnchanged()` on a layer that no longer holds the text the change was based on. Carries that layer's current text, to apply the change to instead. Unchecked. |

Those five are the exceptions this library throws, and they arrive identically whichever
provider a block names. Four of them are its own types; `UncheckedIOException` is the JDK's. **Everything a model call throws belongs to the provider instead**, and those types are
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

**The guarantee covers a failing call, not a failed attempt to make one.** A provider may use
your API key before it sends anything. GLM does: it splits the key on `.` and signs a token
with the two halves, so a key of another shape fails while the request is being assembled, and
throws a plain JDK or JWT exception that is not a `LangChain4jException` at all. This library
refuses such a key when the configuration loads, as a `ConfigValidationException`
([ADR-0049](../adr/0049-validate-a-credentials-shape-when-the-provider-requires-it.md)), so
you meet it at startup instead of at the first request. A provider module this library does
not ship can still surprise you in the same way.

The library does not translate these, and
[ADR-0033](../adr/0033-provider-exceptions-pass-through-untranslated.md) records why:
translating means proxying every model call, which would stop the registry handing back
genuine LangChain4j objects.

---

## Reload semantics

A reload runs when a watched file has been quiet for the debounce period, when your code
calls [`reload()`](#asking-for-a-reload), and as the first half of a
[`store()`](#storing-a-layer-back). Whichever started it, it then:

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
- **Listeners run after the swap**, so `get()` inside one already sees the new snapshot. They
  run on whichever thread started the reload — the watcher thread, or your own caller of
  `reload()`; see [Threading and lifecycle](#threading-and-lifecycle). A `store()` runs none of
  them at all: its caller made the change and is given it back.

**Names appearing and disappearing.** A name added to the file appears in the registry; a name
removed from it is removed, and `get()` on it then throws. Long-running code holding a name
must be ready for that — the console example catches it and returns to its menu.

**Superseded bundles are not closed.** An in-flight request may still hold one. They become
eligible for garbage collection when nothing references them.

---

## The watcher

The watcher is `FileChangeNotifier`, the `ChangeNotifier` the library ships for layers held in
files. `watch(true)` builds one over the layers that are files, whether they were given to
`configFiles(...)` or to `sources(...)`; everything below describes what that one does, and
none of it applies to a layer that is not a file.

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

Measured on one Linux machine — AMD Ryzen 7 7840HS, ext4 on NVMe, Pop!_OS 24.04 (kernel
7.0.11), Temurin 25 — over inotify, write to event observed, 20 samples:

| min | median | max |
|---|---|---|
| 0.37 ms | **0.50 ms** | 0.63 ms |

One machine is not a benchmark. What the numbers say is that the notification is push-based
and costs well under a millisecond there; a slower disk or a busy machine moves them, and no
part of the design depends on the value.

Add the debounce, so a saved file is live roughly 300 ms later by default. Events for one
logical write arrived within ~2.5 ms on the same machine, which is the burst the default is
chosen to cover. Lowering it below the time your writer takes to finish produces reloads of
half-written files, which are rejected as failures rather than applied.

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
| `io.github.maxtrezzi.modelrack4j.WritableFileConfigSource` | `WARN` | A store ended — applied or rejected — and its temporary file could not be removed. A `.modelrack4j-staged-*.conf` file is left beside the layer, which nothing reads. Whether the layer changed is what the store itself returned or threw, not what this line says. |
| `io.github.maxtrezzi.modelrack4j.WritableFileConfigSource` | `DEBUG` | A layer's path could not be resolved to a real file; the permissions of the file being replaced could not be copied. |

Successful reloads are **not** logged. If you want that, register `onReload` and log what it
gives you.

The unconditional `WARN` is deliberate. A typo in a config file does not delay one reload — it
makes every later edit to that file fail identically, so without the log the only symptom is a
model that quietly stops reflecting the file.

**One case where it does not arrive: running an example through Maven.** `exec:java` runs
inside Maven's own process, and `mvn -q` sets the system property
`org.slf4j.simpleLogger.defaultLogLevel` to `error` there. The examples bind `slf4j-simple`,
which reads that same property, so the library goes quiet with Maven. Setting the level for one
logger — `-Dorg.slf4j.simpleLogger.log.io.github.maxtrezzi.modelrack4j.LlmRegistry=warn` — brings
it back and leaves `-q` in place.

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

Read out of the LangChain4j 1.20.0 artifacts rather than from documentation. Gemini is the
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
  from any other community model — the registry name is the reliable discriminator. Its
  `api-key` is the one credential in this library with a required shape: `id.secret`, with a
  secret of at least 16 bytes. The provider signs a token with the two halves rather than
  sending the key, so any other shape is refused when the configuration loads.
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
    default boolean supportsModeration() { return true; }             // can it build a ModerationModel?
    void validate(LlmConfig config);                                  // anything core cannot see
    ChatModel createChatModel(LlmConfig config);
    Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config);
    Optional<ModerationModel> createModerationModel(LlmConfig config);
    Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config);
}
```

**Report a capability; do not enforce it.** `tokenEstimation()` and `supportsModeration()`
say what your provider can do, and core turns each into the rejection and the message. So
every provider that cannot moderate refuses the same configuration in the same words, and a
new rule is written once instead of once per module. Do not repeat these checks in
`validate()`.

`tokenEstimation()` is three-valued rather than boolean on purpose. Every provider except GLM
ships an estimator, so a boolean would return true almost everywhere and accept every
configuration — the check would exist and catch nothing. What varies is the *cost*.

`supportsModeration()` defaults to `true`, which is not a claim that most providers moderate.
It is what keeps a factory written before this method existed working unchanged: it does not
override the method, core lets the configuration through, and the missing model is still
caught a moment later when `createModerationModel` returns empty. Override it and the user
gets a better message, earlier.

`validate()` is for what core cannot see — a rule specific to your provider. Throw
`ConfigValidationException` with a message naming the block and the way out; those messages
are part of the contract, and the tests assert on them. Three of the four factories in this
repository have an empty `validate()`: they used to reject moderation here, and that is
reported through `supportsModeration()` instead, while OpenAI never had anything to reject.
The fourth is GLM, which checks the *shape* of the API key, because its own code splits the
key and signs a token with the halves before any call. An empty body is the normal case, not
a sign of an unfinished provider.

---

## Threading and lifecycle

- **One daemon thread**, named `modelrack4j-config-watcher`, and only when `watch(true)`. With
  watching off, the library starts nothing of its own.
- **Publishing is one assignment.** The live snapshot is a single volatile field, so a reader
  needs no lock and never waits.
- **Reloads run one at a time.** A reload can now be started by the watcher thread or by your
  call to `reload()`, so the registry holds a lock while it reloads. Two reloads at once would
  both read the same old snapshot, both build, and the later one would quietly throw the
  earlier one away after its listeners had already announced it.
- **`get()`, `snapshot()` and `names()` are safe from any thread**, during a reload included,
  and never take that lock. A reader either sees the whole old snapshot or the whole new one.
- **Listeners run on the thread that caused the reload** — the watcher thread, or the caller
  of `reload()`. Long work in a listener delays the next reload; hand it off to your own
  executor if it is not quick. A listener must not call `reload()`, because it is already
  running inside one.
- **A store takes the same lock, and holds it across the layer's own write.** So
  `store()` and `storeIfUnchanged()` are serialised against reloads and against each other,
  and a slow `WritableConfigSource.write` — a database that is not answering — delays the
  next reload for as long as it runs. Readers are unaffected: they never take that lock.
- **Listeners may be registered at any time, from any thread.** The registration lists are
  copy-on-write, so adding one during a reload is safe and does not block it.
- **Registering a listener does not replay anything.** It is called on the next reload, never
  for one that already happened; the current state is `get()`, not a callback.
- **`close()` does not wait for a reload another thread is running.** That reload finishes
  on its own and its listeners run, so a listener can still be called after `close()` has
  returned. If your application must not be called back after closing, check for that in the
  listener itself.
- **Do not call `close()` from a reload listener.** The listener runs inside the reload, which
  holds the reload lock, and closing waits for the notifier's own thread — which may be
  waiting for that same lock. With `FileChangeNotifier` the call returns after its five-second
  timeout instead of at once; a notifier of your own that waits without a timeout never
  returns at all. Close the registry from the code that owns it.
- **Bundles are never closed by the library**, including superseded ones.

---

## What you still write yourself

The library configures models. Everything LangChain4j offers on top of a model — `@Tool`
methods, RAG retrievers, guardrails — is registered on an `AiServices`, and an `AiServices` is
built from a `ChatModel`. That is what an `LlmBundle` holds, together with the
`StreamingChatModel`, the `ModerationModel` and the `ChatMemoryProvider` an AiService can also
take. So none of this is affected by the library, and you write it as you would on plain
LangChain4j:

```java
interface Assistant {
    @SystemMessage("You are terse.")
    String ask(String question);
}

LlmBundle bundle = registry.get("SL");

AiServices<Assistant> building = AiServices.builder(Assistant.class)
        .chatModel(bundle.chatModel())
        .tools(new ClockTool());
bundle.chatMemoryProvider()
        .ifPresent(provider -> building.chatMemory(provider.get(userId)));   // your own id

String answer = building.build().ask(question);
```

Two rules for that code:

- **Build the AiService where you use it, not once at start-up.** It captures the objects it
  is given, so an assistant built at start-up holds that snapshot's `ChatModel` for the life
  of the process, and a reload never reaches it. It is the caching mistake of
  [Using it](#using-it) one level higher up. An AiService is a proxy over the objects passed
  in, so building one per request is affordable.
- **Moderation is ignored on the `AiServices` streaming path.** Upstream behaviour, described
  under [Providers](#providers).
- **RAG needs an `EmbeddingModel`, and the library does not configure one** — it is
  [out of scope](#out-of-scope) for v1. Build it in code and pass it to your retriever. The
  chat model in the same AiService still comes from the bundle.

`ConsoleChat` runs exactly this: `/tools` during a chat sends the following questions through
an AiService with a clock tool, rebuilt each turn on the bundle that turn fetched.

---

## Out of scope

Deliberate and permanent:

- **Configuring `AiServices`, `@Tool` methods, RAG retrievers or guardrails from a file.**
  Code-shaped, not config-shaped: no configuration file can express an interface or a method
  body. Using them is not restricted — this library builds the inputs you pass to
  `AiServices`, and [What you still write
  yourself](#what-you-still-write-yourself) shows how.
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
| A rejected reload logs nothing, running an example with `mvn -q … exec:java` or a `run-*.sh` script | `-q` makes Maven set the SLF4J level to `error` for its own process, and `exec:java` runs the example inside it. The launchers pass `-q` too | Add `-Dorg.slf4j.simpleLogger.log.io.github.maxtrezzi.modelrack4j.LlmRegistry=warn`, or drop `-q`. Your own application is not affected. |
| `A mandatory substitution is unresolved` | `${VAR}` with the variable unset | Export it, or override the key in a higher layer. Do not switch to `${?VAR}`. |
| `ships no moderation model` | `moderation.enabled = true` on Anthropic, Gemini or GLM | Only OpenAI has one. Route moderation through an OpenAI configuration. |
| `counts tokens by calling its API` | `token-window` on a remote counter | Add `allow-remote-token-counting = true`, or use `message-window`. |
| `no token count estimator` | `token-window` on GLM | Use `message-window`. No flag helps. |
| `is not shaped like a GLM key` | `api-key` on GLM is not `id.secret`, or its secret half is under 16 bytes | GLM signs a token with the two halves instead of sending the key, so a wrong shape fails before any call. Copy the key again in full. |
| `` `temperature` is deprecated for this model `` | A non-default `temperature` on a model that rejects one, such as `claude-sonnet-5` | Remove the key. The model then uses its own sampling settings. |
| `UnknownConfigurationException` at runtime | The name was removed from the configuration while running | Catch it and re-read `names()`, or keep the block. The exception's `configurationName()` gives the name that was asked for. |
| `watch(true) watches configuration files, and none of these layers is one` | `watch(true)` where no layer is a file — every one is a database row, or your own `ConfigSource` | There is nothing the library can watch. Call `reload()` when the configuration changes, or pass a `ChangeNotifier`. One file layer among the others is enough to watch that file. |
| `Configuration source … cannot be read` | A layer's file is missing or unreadable, at `build()` or at any reload. Calling `text()` on the source yourself says `Configuration file does not exist or is not readable` instead | Check the path and the permissions. This is a `ConfigAccessException`, not a validation failure: nothing was found wrong with the text, because the text could not be read. |
| `Cannot write the configuration …`, `Cannot replace the configuration file …` | A `store` could not write the layer — the directory is not writable, the disk is full. The message names that directory; the cause after the last colon, for example `java.nio.file.AccessDeniedException`, names the temporary file the write goes through rather than your configuration — and when it is the directory that refused, that file was never created | The previous configuration is still live and the layer still holds its old text; nothing was half-applied. Also a `ConfigAccessException`. Making the **file** writable does not help — it is the directory that is written. |
| Something escapes a `catch (ConfigValidationException)` that used to catch it | A layer that cannot be reached now throws `ConfigAccessException`, which is deliberately not a subclass | Catch both types where you want the previous behaviour. The split is what lets an application answer `400` for a text it cannot accept and `503` for a disk it cannot write. |
| `Configuration sources must have distinct ids` | Two layers with the same id — often one file listed twice | Remove the repeat. File ids are the absolute path, so two spellings of one file count as one. |
| An `include` in a layer adds nothing, and nothing is logged | The layer is not a file, so the include is looked up on the classpath, and a HOCON include that finds nothing is not an error | Includes work in file layers. For a layer from a database, assemble the text before handing it over. |
| `StaleLayerException` on an edit nobody else made | The `expected` text lost the layer's trailing newline on the way in — a shell `expected=$(cat layer.conf)` strips it, and the comparison is byte for byte | Carry the layer's text without reshaping it: start from `text()`, or read the file in a way that keeps the last byte. |
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

**Published to Maven Central** under `io.github.maxtrezzi`, signed, with a sources jar and a
javadoc jar for each artifact. `modelrack4j-examples` is not published: it is demo code, and
you read it in the repository rather than depending on it.

**LangChain4j is pinned** at 1.20.0, imported through both its stable and community BOMs. The
capability matrix on this page is a fact about that version, not a permanent property — it is
re-verified against the artifacts on every bump.

---

## See also

- **[Part 1 — Tutorial](part-1-tutorial.md)** — the guided path from nothing to a running
  console chat.
- **[`docs/adr/`](../adr/README.md)** — why each of these decisions was made, and what was
  rejected. Every rule on this page has an ADR behind it.
