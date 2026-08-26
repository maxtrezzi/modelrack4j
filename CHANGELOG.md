# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**Pre-1.0 versioning.** While the version is `0.x`, the public API may change in a minor
release. Breaking changes will be called out under **Changed** with the migration, but they
will not be held back for a major bump until the API settles at `1.0.0`.

## [0.1.0] — unreleased

The first release. Everything below is new, so it is grouped by what it gives you rather
than listed as one long **Added** block.

### The registry

- `LlmRegistry`, built from a list of HOCON files given **lowest precedence first**, merged
  into one snapshot and resolved exactly once after merging — so a `${VAR}` in a lower layer
  that a higher layer overrides never has to resolve.
- `registry.get(name)` returns the current `LlmBundle`; `names()` lists what is configured.
- `registry.snapshot()` returns an `LlmSnapshot`: one generation held still, so several
  lookups are guaranteed to agree with each other. `get()` reads the live configuration on
  every call, so two consecutive calls can straddle a reload — rare, reproducible, and a
  correctness hazard where several models must be consistent. A snapshot never updates:
  take one per unit of work.
  Bundles are keyed by **configuration name**, never provider name, so two names may share
  one provider and differ only in parameters.
- `LlmBundle` carries a `ChatModel` plus an `Optional` `StreamingChatModel`,
  `ModerationModel` and `ChatMemoryProvider`, each present only when configured and
  supported.
- `LlmConfig` and the sealed `MemoryConfig` are validating records: an instance that exists
  is valid.
- An optional `description` key on each named block: one short line saying what the
  configuration is for, surfaced through `LlmConfig.description()`. Nothing in the library
  reads it. A present-but-blank description is rejected; `description = null` in a higher
  layer clears one set lower down.
- Configuration errors are `ConfigValidationException` and name the offending block; an
  unknown name is `UnknownConfigurationException`.

### Hot reload

- `Builder.watch(boolean)` (off by default) and `Builder.debounce(Duration)` (300 ms).
- Reload is **atomic across the whole snapshot**: parse, validate and build every changed
  bundle in a staging area, then swap one reference. Any failure anywhere swaps nothing and
  fires `onReloadFailure` once, leaving the previous snapshot live.
- Every rejected reload is logged at `WARN` with its cause, whether or not an
  `onReloadFailure` listener is registered.
- Exactly one `onReload(ReloadChange)` per successful reload, carrying `updated`, `added`
  and `removed` name sets — never one callback per name.
- Unchanged blocks keep the same bundle instance; the diff is record equality on the parsed
  config.
- The watcher handles temp-file-then-rename saves, Kubernetes ConfigMap symlink swaps, and a
  watched directory being deleted and recreated.
- A name removed from configuration is removed from the registry. Superseded bundles are not
  closed — in-flight requests may still hold them.
- `LlmRegistry` is `AutoCloseable`; closing stops the daemon watcher thread.

### Providers

- `modelrack4j-provider-openai`, `-anthropic`, `-gemini` and `-glm`, each discovered through
  `ServiceLoader`. Core depends on no provider artifact.
- Capability validation at load time: moderation is rejected on the three providers whose
  LangChain4j module ships no `ModerationModel`.
- Token-window memory follows the provider's counting cost — built freely where counting is
  local (OpenAI), opt-in via `allow-remote-token-counting` where it is a billed HTTP call
  (Anthropic, Gemini), and rejected outright where no estimator exists (GLM).
- `ProviderFactory` is the SPI for adding your own.

### Documentation and examples

- A manual in `docs/manual/`: a tutorial built on the runnable examples, and a reference for
  the schema, the API, reload semantics, the provider matrix and troubleshooting.
- `AtomicSnapshot`, an example that demonstrates snapshot-wide reload atomicity: four threads
  sample two models while one save changes both, and the mixed pair never appears. Needs no
  API key and sends no request, so it costs nothing to run.
- `ProviderSwap`, an example that changes a running application's provider by editing a file
  and asks the same question again through the same call site.
- `ConsoleChat`, an interactive example: a menu of every configured model, chat with the one
  you pick, `/menu` to switch, `/exit` to leave. Watches its configuration files, so editing
  one while it runs changes the menu underneath you.
- `ThreeModelCouncil`, an example that asks one question of three models configured together
  and prints the three answers, with no provider branch anywhere in the code.

### Build and artifacts

- Java 17 baseline, built and tested on JDK 17, 21 and 25.
- Built against LangChain4j 1.19.0, with both the stable and community BOMs imported.
- `modelrack4j-bom` versions every artifact from one coordinate.
- Sources and javadoc jars attach to every published module.
- `LICENSE` and a `NOTICE` file ship inside `META-INF/` of every jar. The `NOTICE` is four
  lines and stays that way on purpose: Apache 2.0 §4(d) requires anyone redistributing this
  library, or a derivative of it, to carry its attribution along.
- Integration tests are off unless `-Pintegration` is passed, and each skips itself when its
  own API key is absent.

### Known limitations

- **Not published to Maven Central.** `mvn install` locally; publishing is a later
  milestone.
- **macOS reload latency is unmeasured.** The JDK's `WatchService` is polling-based there;
  the figures in the README are Linux only.
- Moderation is silently ignored by LangChain4j on the `AiServices` streaming path
  ([langchain4j#2779](https://github.com/langchain4j/langchain4j/issues/2779)).
- GLM has no whole-call timeout: its client's `callTimeout` and `writeTimeout` are
  deprecated upstream, so `timeout` maps to connect and read only.
- No `EmbeddingModel`, and no `ReloadableChatModel` hot-swap wrapper — both deliberate, see
  the README's scope section.

<!--
  No link definition for [0.1.0] yet: the repository is public but deliberately not released,
  so there is no v0.1.0 tag to compare against (ADR-0034). The link lands with the tag.
-->
