# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**Pre-1.0 versioning.** While the version is `0.x`, the public API may change in a minor
release. Breaking changes will be called out under **Changed** with the migration, but they
will not be held back for a major bump until the API settles at `1.0.0`.

## [Unreleased]

### Security

- **`LlmConfig.toString()` no longer prints the API key.** It renders `apiKey=***` and keeps
  every other component. The value in that field is the credential after substitution — the
  real key, not the `${VAR}` your file was written with — so the `toString()` a record
  generates would put it in any log line that printed a config or a bundle. `equals` and
  `hashCode` are unchanged and still compare the key, so rotating a credential is still a
  configuration change that triggers a reload. If you need the value in your own logs, print
  the fields you want rather than the record
  ([ADR-0047](docs/adr/0047-redact-the-credential-from-llmconfig-tostring.md)).

### Added

- **`ProviderFactory.supportsModeration()`**, a `default` method returning `true`. A provider
  reports whether it can build a `ModerationModel`, and core turns that into the rejection
  and its message. Your own factory keeps working unchanged: it does not override the method,
  core lets the configuration through, and a missing model is still caught when
  `createModerationModel` returns empty. Override it to `false` and your users get a clearer
  message, sooner
  ([ADR-0048](docs/adr/0048-providers-report-capabilities-core-enforces-them.md)).

### Changed

- Enabling `moderation.enabled = true` on Anthropic, Gemini or GLM is now refused by core
  rather than by each provider. The failure and the type are the same
  (`ConfigValidationException`, before any model is built); the wording changed from
  *"has no moderation model"* to *"ships no moderation model"*. Only code asserting on that
  exact string is affected.

### Fixed

- A store that failed while writing its temporary file left that file behind, beside the
  configuration, with nothing able to remove it. It is now deleted on every failure path.
- `WritableFileConfigSource` tested for the layer's presence in a way that followed symbolic
  links, so a link pointing at itself or around a cycle was treated as a file that is not
  there instead of one that cannot be resolved. The check no longer follows the final link.
- In the `ThreeModelCouncil` example, one model failing to answer ended the whole session and
  discarded the answers already printed — so an expired key on one provider cost every answer
  in the round, including the ones already paid for. The failure is now reported against the
  model that produced it, the remaining models still answer, and a round that lost a member
  says so.

## [0.1.0] — 2026-09-02

The first release. Everything below is new, so it is grouped by what it gives you rather
than listed as one long **Added** block.

### The registry

- `LlmRegistry`, built from a list of HOCON layers given **lowest precedence first**, merged
  into one snapshot and resolved exactly once after merging — so a `${VAR}` in a lower layer
  that a higher layer overrides never has to resolve.
- A layer does not have to be a file. `Builder.configFiles(List<Path>)` is the shorthand for
  the common case; `Builder.sources(List<ConfigSource>)` takes layers from anywhere, so a
  configuration can live in a database row or come from a configuration service, and files
  and other sources mix in one list. A `ConfigSource` is an id and its text, and the id is a
  label for error messages rather than an address the library resolves.
- `registry.get(name)` returns the current `LlmBundle`; `names()` lists what is configured.
- `registry.snapshot()` returns an `LlmSnapshot`: one generation held still, so several
  lookups are guaranteed to agree with each other. `get()` reads the live configuration on
  every call, so a reload can land between two consecutive calls — rare, reproducible, and a
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
  layer clears a description set in a lower layer.
- Configuration errors are `ConfigValidationException` and name the offending block; an
  unknown name is `UnknownConfigurationException`.

### Hot reload

- `Builder.watch(boolean)` (off by default) and `Builder.debounce(Duration)` (300 ms), for
  layers given as files.
- `registry.reload()` re-reads every layer on demand and returns what changed, or an empty
  `Optional` when nothing did. It is what a layer nothing can watch — a database row — uses
  instead of the watcher, and it throws when the new configuration is rejected, leaving the
  previous one live. Reloads run one at a time, no matter who asks for one; readers never wait.
- `registry.store(layer, text)` writes a layer back: it validates the whole configuration
  against the new text, applies it, and only then stores it, so a text that would not load is
  refused with nothing written and nothing changed. If storing fails, the previous
  configuration comes back and the call throws. A store fires no reload listener — the caller
  made the change and is given it back as the return value — and a file watcher waking up
  afterwards finds the configuration already live and publishes nothing. The target must be a
  `WritableConfigSource`, which `ConfigSource.ofWritableFile(Path)` provides for a file;
  reading a layer says nothing about whether it may be written.
- `registry.storeIfUnchanged(layer, expected, text)` is the same, but only while the layer
  still holds `expected`, compared character for character. Otherwise it throws
  `StaleLayerException`, which carries the text the layer holds now, to apply the change to
  and try again. Use it wherever more than one writer is possible: a plain `store` is atomic
  against reloads and other stores, but it cannot hold your read and your store together.
- `ChangeNotifier` is the extension point for telling the registry that configuration
  changed by a mechanism the library does not provide, such as a database `LISTEN`/`NOTIFY`.
  `Builder.watch(true)` builds the file one for you. That one is `FileChangeNotifier`, and it
  is public: build it yourself with `FileChangeNotifier.of(files, debounce)` and pass it to
  `Builder.notifier(...)` when only some of your layers are files, so the file half is still
  watched. A `close()` must not wait forever — it can be called while a reload is running.
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
- `LlmRegistry` is `AutoCloseable`; closing stops the notifier, and with it the daemon
  watcher thread when watching was enabled.

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
  sample two models while one save changes both, once through two `get()` calls and once
  through a shared `snapshot()`. The `snapshot()` column never shows a mixed pair; the
  `get()` column occasionally does, and `registry.snapshot()` is how a caller avoids that.
  Needs no API key and sends no request, so it costs nothing to run.
- `ProviderSwap`, an example that changes a running application's provider by editing a file
  and asks the same question again through the same call site.
- `ConsoleChat`, an interactive example: a menu of every configured model, chat with the one
  you pick, `/menu` to switch, `/exit` to leave. Watches its configuration files, so editing
  one while it runs changes the menu underneath you. `/tools` answers through an `AiServices`
  proxy with a `@Tool` method instead of calling the model directly — built on the bundle
  that turn fetched, which is how a reload reaches code that uses `AiServices`.
- `ThreeModelCouncil`, an example that asks one question of three models configured together
  and prints the three answers, with no provider branch anywhere in the code. The questions
  are read from standard input, one after another, until you type `/exit`. There is no
  default question, because each one costs a request per model.
- `DatabaseSource`, an example whose configuration is held in memory rather than in a file,
  standing in for a database row, driven by the application itself. It shows every answer
  `reload()` gives — a name added, a name updated, nothing changed, and a rejected reload
  after which the previous configuration is still live — and then the same rejected change
  through `store()`, which refuses it before the row is written rather than after. It needs
  no key.
- One script per example at the repository root — `run-atomic.sh`, `run-database.sh`,
  `run-swap.sh`, `run-chat.sh`, `run-council.sh`. Each installs the project first if it has
  to, because `exec:java` resolves `modelrack4j-core` from `~/.m2` rather than from the
  reactor, and each
  `--help` says what that example shows, what it costs, which keys it needs and the plain
  `mvn` command for Windows, where there are no `.bat` counterparts. They also keep the JDK
  warning about `sun.misc.Unsafe` out of the example's output: `exec:java` runs inside the
  Maven process, and it is Maven's own bundled Guava that triggers it, not this project.
- The configuration the file-driven examples read is `examples.conf`. It was called
  `council.conf`, which named only one of the two examples that use it.
- The bundled examples set no `temperature` on their Anthropic blocks. Anthropic has
  deprecated a non-default `temperature` on `claude-sonnet-5`, where the model's adaptive
  thinking controls its own sampling and the API answers a non-default value with a 400. If
  you copied `examples.conf` from an earlier draft, remove that line. `gpt-5.1` still accepts
  one.

### Build and artifacts

- Java 17 baseline, built and tested on JDK 17, 21 and 25.
- Built against LangChain4j 1.19.0, with both the stable and community BOMs imported.
- `modelrack4j-bom` versions every artifact from one coordinate.
- Sources and javadoc jars attach to each of the five modules that produce a jar — core and
  the four providers. The parent and `modelrack4j-bom` publish as POMs.
- `LICENSE` and a `NOTICE` file ship inside `META-INF/` of every jar. The `NOTICE` is four
  lines and stays that way on purpose: Apache 2.0 §4(d) requires anyone redistributing this
  library, or a derivative of it, to carry its attribution along.
- Integration tests are off unless `-Pintegration` is passed, and each skips itself when its
  own API key is absent.

### Known limitations

- **macOS reload latency is unmeasured.** The JDK's `WatchService` is polling-based there;
  the figures in the README come from one Linux machine, which is named beside them.
- Moderation is silently ignored by LangChain4j on the `AiServices` streaming path
  ([langchain4j#2779](https://github.com/langchain4j/langchain4j/issues/2779)).
- GLM has no whole-call timeout: its client's `callTimeout` and `writeTimeout` are
  deprecated upstream, so `timeout` maps to connect and read only.
- No `EmbeddingModel`, and no `ReloadableChatModel` hot-swap wrapper — both deliberate, see
  the README's scope section.

<!--
  The tag is created after the release is published, never before: Maven Central accepts a
  version once and forever, so a tag written first would be a promise the publish might not
  keep (ADR-0045).
-->
[0.1.0]: https://github.com/maxtrezzi/modelrack4j/releases/tag/v0.1.0
