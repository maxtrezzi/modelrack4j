# Part 1 — Tutorial

**What this is for.** Your application's models — which provider, which model, what
temperature, how much memory — are normally decided in Java, which means changing any of them
is a recompile and a redeploy. modelrack4j moves those decisions into configuration files that
can be edited while the application runs, and validates them against what each provider can
actually do. The argument for that — and the cases where you should reach for your framework's
LangChain4j starter instead — is in the README's [Why](../../README.md#why). This page assumes
you are past it and want the thing working.

Forty minutes, start to finish. You will end with a console application talking to a real
model, a configuration file you can edit **while it runs**, and a clear idea of what the
library refuses to do and why.

Every command and every output block on this page was run before it was written down. Where
the output depends on a model's answer, it says so instead of inventing one.

> **Most of it is free, and the free part comes first.** Steps 1, 2, 6, 7 and 8 send no
> request at all: building, writing configuration, watching validation refuse a block a
> provider cannot serve, and watching a broken file get rejected are all offline. Only
> steps 3, 4, 5 and 9 talk to a provider — roughly a dozen short prompts if you follow the
> page literally, of which step 9 sends three, one per model.
>
> **Every step needs one API key to be *set*, because substitution is mandatory** — an unset
> variable fails at load, by design. Only those four steps need it to be valid and funded.
> Steps 1 to 8 take one key from any supported provider; step 9 needs two.

**Contents**

| | | |
|---|---|---|
| [Before you start](#before-you-start) | what you need | |
| [1. Build and install](#1-build-and-install) | why `install` and not `package` | offline |
| [2. Your first configuration](#2-your-first-configuration) | one block, one model | offline |
| [3. Talk to it](#3-talk-to-it) | the menu, `/menu`, `/exit` | **sends requests** |
| [4. Add a model while it is running](#4-add-a-model-while-it-is-running) | the point of the library | **sends requests** |
| [5. Memory and streaming](#5-memory-and-streaming) | two optional parts of a bundle | **sends requests** |
| [6. What it refuses to build](#6-what-it-refuses-to-build) | three deliberate failures | offline |
| [7. Break the file on purpose](#7-break-the-file-on-purpose) | what a rejected reload does | offline |
| [8. Layering](#8-layering) | defaults, environment, local override | offline |
| [9. Three models at once](#9-three-models-at-once) | the council | **sends requests** |
| [10. In your own project](#10-in-your-own-project) | the dependency and ten lines of Java | offline |

---

## Before you start

| You need | Check with |
|---|---|
| JDK 17 or newer | `java -version` |
| Maven 3.8.7 or newer | `mvn -version` |
| One provider API key | see below |

Export whichever key you have. The tutorial's examples use Anthropic and OpenAI:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
```

Gemini uses `GEMINI_API_KEY` and GLM uses `ZHIPU_API_KEY`. Any one of the four is enough for
steps 1 to 8 — swap the `provider` and `model-name` in the configuration for the ones from
[Part 2](part-2-reference.md#providers).

---

## 1. Build and install

```bash
git clone https://github.com/maxtrezzi/modelrack4j.git
cd modelrack4j
mvn install
```

Roughly a minute. It runs the full test suite, which needs no keys and no network access to
any provider.

> **`install`, not `package`.** The examples run through `exec:java`, which resolves
> `modelrack4j-core` from your local repository rather than from the build you just ran. With
> only `package`, you get a stale jar or none at all — and if the version in `~/.m2` is from
> an older checkout, the failure is a `NoSuchMethodError` at startup rather than anything that
> mentions installing. Re-run `mvn install` after pulling changes.

---

## 2. Your first configuration

Make a directory to work in — anywhere, it does not have to be inside the checkout — and
write one file:

```bash
mkdir -p ~/modelrack4j-tutorial
```

```hocon
# ~/modelrack4j-tutorial/llm.conf
llm {
  SL {
    description = "my first model"
    provider    = anthropic
    api-key     = ${ANTHROPIC_API_KEY}
    model-name  = "claude-sonnet-5"
  }
}
```

Four things to notice, because they are the whole schema in four points:

- **`llm` is the root.** Everything the library reads lives under it.
- **`SL` is a name you invented.** It is how your code will ask for this model. Short names
  are normal — they get typed on every lookup.
- **`${ANTHROPIC_API_KEY}` is mandatory substitution.** If the variable is unset, loading
  fails immediately and says so. Do not use `${?ANTHROPIC_API_KEY}`: the optional form
  silently yields nothing, and you find out at the first request instead, as an
  authentication error.
- **`description` is for humans.** Nothing in the library reads it. It shows up in the menu
  you are about to see.

Everything else has a default. You did not set a timeout, a temperature, streaming, memory or
moderation, and the configuration is complete.

---

## 3. Talk to it

From inside the checkout:

```bash
mvn -q -pl modelrack4j-examples exec:java \
    -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.ConsoleChat \
    -Dexec.args=$HOME/modelrack4j-tutorial/llm.conf
```

```
modelrack4j console
watching [/home/you/modelrack4j-tutorial/llm.conf] — edit and save while this runs.

configured models
  1  SL           anthropic / claude-sonnet-5
         my first model
choose 1-1 by number or name, or /exit:
```

Type `1`, then ask it something:

```
chatting with SL — my first model (no memory configured: each turn is independent)
/menu for the menu, /exit to quit.

you> what is a HOCON substitution?
SL> <the model's answer>
```

`/menu` goes back to the list. `/exit` leaves.

That parenthesis — *no memory configured: each turn is independent* — is not a limitation of
the example. It is your configuration file being reported back to you: you did not ask for
memory, so no memory was built, so each question stands alone. Step 5 changes that.

**Leave the application running for the next step.**

---

## 4. Add a model while it is running

In another terminal, append a second block to the same file:

```hocon
llm.SH {
  description = "the same model, streamed, for long answers"
  provider    = anthropic
  api-key     = ${ANTHROPIC_API_KEY}
  model-name  = "claude-sonnet-5"
  streaming   = true
}
```

Save it, and watch the terminal you left running:

```
you>
  [config reloaded: updated=[] added=[SH] removed=[]]
```

Type `/menu`:

```
configured models
  1  SH           anthropic / claude-sonnet-5  streaming
         the same model, streamed, for long answers
  2  SL           anthropic / claude-sonnet-5
         my first model
choose 1-2 by number or name, or /exit:
```

**No restart.** The new model is there, and `SL` — which you did not touch — is not merely
still working, it is the *same object it was before the reload*. Only what changed was
rebuilt.

Try the other direction too: delete the `SH` block while you are chatting with it. You get
`removed=[SH]` and the console drops you back to the menu, because the name it was using no
longer exists.

**The same edit changes the provider.** If you have a key for a second provider, change three
lines of `SL` — `provider`, `api-key` and `model-name` — and save:

```hocon
    provider    = openai
    api-key     = ${OPENAI_API_KEY}
    model-name  = "gpt-5.1"
```

Your next question goes to a different company, through the same code, with no restart.
Nothing in `ConsoleChat` names a provider or branches on one. `ProviderSwap` does exactly this
unattended, asking the same question before and after the edit, if you would rather watch it
than type it.

This is the feature the rest of the library exists to make safe. Part 2 explains
[what a reload guarantees](part-2-reference.md#reload-semantics) — the short version is that
either all of it applies or none of it does.

> **Want to see the "all of it" part?** `AtomicSnapshot` changes two models in one save while
> four threads read both as fast as they can, and reports how many times they caught a mixed
> pair. It reads configuration only, so it needs no key and costs nothing:
> `mvn -q -pl modelrack4j-examples exec:java -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.AtomicSnapshot`

---

## 5. Memory and streaming

Two of the four parts of a bundle are optional and driven entirely by configuration. Edit
`SL` to give it memory:

```hocon
  SL {
    description = "short and cheap — the everyday answer"
    provider    = anthropic
    api-key     = ${ANTHROPIC_API_KEY}
    model-name  = "claude-sonnet-5"
    memory { type = message-window, max-messages = 20 }
  }
```

```
configured models
  1  SH           anthropic / claude-sonnet-5  streaming
         the same model, streamed, for long answers
  2  SL           anthropic / claude-sonnet-5  message-window
         short and cheap — the everyday answer
```

Chat with `SL` now and the parenthesis is gone: tell it your name, ask what it is two
questions later, and it knows. Chat with `SH` and the answer arrives a word at a time,
because that block asked for `streaming = true`.

Nothing in the example special-cases either model. It asks the bundle what it has:

```java
if (bundle.streamingChatModel().isPresent()) { ... } else { ... }
```

The configuration decides; the code adapts. That is the shape the library is for.

---

## 6. What it refuses to build

Three configurations that fail, on purpose. Each one fails **when the file loads**, not on
the first request — which is the entire point of validating against what the provider can
actually do.

**Moderation on a provider that has none:**

```hocon
llm.SL { provider = anthropic, api-key = ${ANTHROPIC_API_KEY}
         model-name = "claude-sonnet-5"
         moderation { enabled = true } }
```

```
ConfigValidationException: llm.SL sets moderation.enabled = true, but provider 'anthropic'
ships no moderation model. Remove the moderation block, or route moderation through an
OpenAI-family configuration.
```

Of the four providers, only OpenAI ships a `ModerationModel`. That is a fact about the
LangChain4j artifacts, checked by reading them.

**Token-window memory on a provider that counts remotely:**

```hocon
llm.SL { provider = anthropic, api-key = ${ANTHROPIC_API_KEY}
         model-name = "claude-sonnet-5"
         memory { type = token-window, max-tokens = 2000 } }
```

```
ConfigValidationException: llm.SL uses memory.type = token-window, but provider 'anthropic'
counts tokens by calling its API, so every memory eviction makes a billed, rate-limited
network request. Set memory.allow-remote-token-counting = true to accept that cost.
```

This one is not a refusal, it is a question. Anthropic *can* count tokens — by making an HTTP
call, inside what your code assumes is in-memory bookkeeping. Add the flag if that is what
you want. On OpenAI, which counts locally, no flag is needed. On GLM, which cannot count at
all, the flag makes no difference and the answer stays no.

**A missing environment variable:**

```
ConfigValidationException: A mandatory substitution is unresolved after merging all layers.
Set the environment variable, or override the value in a higher-precedence layer:
/home/you/modelrack4j-tutorial/llm.conf: 4: Could not resolve substitution to a value:
${TUTORIAL_KEY_NEVER_SET}
```

Loud, at startup, naming the file and the line. This is what `${VAR}` buys you over `${?VAR}`.

---

## 7. Break the file on purpose

Start the console again, pick `SL`, and then — while it runs — delete the `model-name` line
and save.

```
you> [modelrack4j-config-watcher] WARN io.github.maxtrezzi.modelrack4j.LlmRegistry -
modelrack4j reload rejected; the previous configuration stays live: llm.SL is not a valid
configuration block: ... No configuration setting found for key 'model-name'

  [config rejected, still running the previous one: llm.SL is not a valid configuration
block: ... No configuration setting found for key 'model-name']
```

Now type `/menu`. `SL` is still there, still answers, still has the model name you deleted
from the file.

**Nothing was applied.** A rejected reload changes nothing at all: the previous snapshot stays
live in full, and the application keeps working on the configuration it already had. Fix the
file, save again, and the next reload succeeds.

Both lines above matter and they come from different places. The `WARN` is the library
logging through SLF4J, and it appears whether or not your code asked for it. The line in
square brackets is the example's own `onReloadFailure` listener. If your application has no
binding on the classpath, SLF4J discards the first one — see
[logging](part-2-reference.md#logging) in Part 2.

---

## 8. Layering

One file is the simple case. Real deployments have a baseline in the image, an environment
file, and something local for development. Split what you have:

```hocon
# defaults.conf — the baseline, checked in
llm {
  SL {
    description = "short and cheap — the everyday answer"
    provider    = anthropic
    api-key     = ${ANTHROPIC_API_KEY}
    model-name  = "claude-sonnet-5"
    timeout     = 60s
  }
}
```

```hocon
# local.conf — yours, not checked in
llm.SL { timeout = 10s }
```

```bash
mvn -q -pl modelrack4j-examples exec:java \
    -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.ConsoleChat \
    -Dexec.args="$HOME/modelrack4j-tutorial/defaults.conf $HOME/modelrack4j-tutorial/local.conf"
```

**Lowest precedence first.** The last file wins on conflict, so `local.conf` overrides
`timeout` — a shorter timeout for an unreliable local connection, instead of the full
minute — and inherits everything else.

The subtle part is worth knowing before it causes a problem. All layers are merged **first** and
resolved **once**, at the end. So a `${VAR}` in a lower layer that a higher layer replaces is
never evaluated — you can ship a `defaults.conf` demanding `${ANTHROPIC_API_KEY}` and override
that whole key locally with a literal, on a machine where the variable does not exist.
If instead you resolve each file as you parse it, which is the obvious implementation, that
same setup fails.

To *remove* something a lower layer set rather than replace it, use `null`:

```hocon
llm.SL { description = null }
```

---

## 9. Three models at once

The scenario the library was built for: several models cooperating, configured together, all
reloaded together. `council.conf` in the checkout defines `SL`, `SH` and `CR` — two Anthropic
models that differ in memory and streaming, and an OpenAI model that also moderates.

```bash
mvn -q -pl modelrack4j-examples exec:java \
    -Dexec.mainClass=io.github.maxtrezzi.modelrack4j.examples.ThreeModelCouncil \
    -Dexec.args=modelrack4j-examples/src/main/resources/council.conf
```

Needs both `ANTHROPIC_API_KEY` and `OPENAI_API_KEY`. It asks each model the same question and
prints what each one has.

Look at what the code does *not* do:

```java
for (String name : registry.names()) {
    LlmBundle bundle = registry.get(name);
    ...
}
```

There is no `if (provider.equals("openai"))` anywhere. The names come from the file, the
capabilities come from the bundle, and adding a fourth model to the council is an edit to
`council.conf` — not a recompile.

This is also why one reload has to swap every bundle at once. If `SL` and `SH` are answering
the same question and a reload updated them one at a time, there would be a window where the
council was running half of one configuration and half of another.

---

## 10. In your own project

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

**Core knows no providers.** Add a module for each provider you configure; each one registers
itself through `ServiceLoader`. Add an SLF4J binding too, or you will not see the warnings
from step 7.

```java
try (LlmRegistry registry = LlmRegistry.builder()
        .configFiles(List.of(Path.of("/etc/myapp/llm.conf")))
        .watch(true)
        .build()) {

    String answer = registry.get("SL").chatModel().chat("hello");
}
```

One rule to take with you, and it is the only one that fails silently:

```java
// ✅  ask at the point of use, every time
String ask(String q) { return registry.get("SL").chatModel().chat(q); }

// ❌  captured once at construction — reload will never reach it
Council(LlmRegistry registry) { this.model = registry.get("SL").chatModel(); }
```

Nothing throws when you get this wrong. The reload happens, the file changes, and your model
quietly stays as it was. Inject the **registry**, not a `ChatModel`.

---

## Where next

- **[Part 2 — Reference](part-2-reference.md)** — the complete schema, the full API, what a
  reload guarantees exactly, the provider matrix, and how to add a provider of your own.
- **[Troubleshooting](part-2-reference.md#troubleshooting)** — symptoms, causes, fixes.
