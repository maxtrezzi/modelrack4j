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

- **`LlmRegistry.sources()`**, the layers the registry was built from, lowest precedence first
  and unmodifiable. It gives an application its writable layer back instead of making it carry
  that reference beside the registry. The same list was already on `ReloadFailure.sources()`,
  which is the one path an application is least likely to have written. Write a layer found
  this way through `store(...)`, never through its own `write(String)`: writing it directly
  stores text that nothing validated, and leaves the registry serving the previous
  configuration until something reloads it.

### Changed

- **Breaking: a layer that cannot be read or written now throws `ConfigAccessException`, not
  `ConfigValidationException`.** The new type is public, unchecked, and deliberately **not** a
  subclass of the old one, so catching one never catches the other. It comes from a file that
  is missing or unreadable — at `build()`, at `reload()`, and from `ConfigSource.text()` — and
  from a store that cannot write: no parent directory, an unwritable directory, a full disk, a
  move that fails. Everything about the *text* stays `ConfigValidationException`: a malformed
  block, a value out of range, an unresolved substitution, a provider that rejects its
  configuration, and the refusal of a symbolic link whose `include` cannot be validated
  honestly.

  **Migration:** if you catch `ConfigValidationException` around `build()`, `reload()`,
  `store()` or `storeIfUnchanged()` and want the old behaviour, catch both types. If you turn
  these into HTTP responses, this is the change that lets you answer `400` for one and `503`
  or `500` for the other — which is why it was made. A `ConfigSource` of your own should throw
  the new type from `text()` and `write(String)` when the medium fails; one that still throws
  `ConfigValidationException` compiles and behaves as before
  ([ADR-0053](docs/adr/0053-a-separate-exception-for-a-layer-that-cannot-be-reached.md)).
- Enabling `moderation.enabled = true` on Anthropic, Gemini or GLM is now refused by core
  rather than by each provider. The failure and the type are the same
  (`ConfigValidationException`, before any model is built); the wording changed from
  *"has no moderation model"* to *"ships no moderation model"*. Only code asserting on that
  exact string is affected.
- A provider that reports token counting and then supplies no estimator is now refused with
  the same wording as the other two capabilities: *"produced no token count estimator"*
  instead of *"supplied no token count estimator"*. The failure and the type are unchanged.
  Only code asserting on that exact string is affected.

- **A GLM `api-key` must have the form `id.secret`, and its secret half must be at least 16
  bytes.** The check runs when the configuration loads and throws `ConfigValidationException`
  naming the block. The reason it is worth a rule: GLM does not send your key, it splits the
  key on `.` and signs a token with the two halves, so a key of another shape failed while the
  first request was assembled — as an `ArrayIndexOutOfBoundsException` or a JWT signing error,
  neither of them a `LangChain4jException`, and neither mentioning a key. Both limits are read
  off the provider's own code, so a key this check refuses could never have completed a call
  ([ADR-0049](docs/adr/0049-validate-a-credentials-shape-when-the-provider-requires-it.md)).

- **A store that cannot write a file layer now says that the *directory* is what it could not
  write.** The message was `Cannot write the configuration beside <file>: <cause>`, and the
  cause named the temporary file the write goes through — a path the caller never saw, and
  one that was never even created when the directory is what refused. It now names the
  directory, says that storing needs *that* to be writable rather than the file, and says what
  the path in the cause is. Making the file itself read-only has always left a store working,
  because the new text is written beside the target and moved onto it; the reference now says
  so.
  Only code asserting on the exact string is affected.
- **A commit that fails on a path reached through a symbolic link now names the file the
  write replaces.** `Cannot replace the configuration file <link>` sent the reader to look at
  the permissions of the link while the failure was on the file it resolves to — which is the
  read-only mounted target this library follows links for in the first place. The message now
  adds `(resolved to <path>, which is the file a write replaces)` when the two differ.
- **`StaleLayerException` says what its comparison includes.** The message adds that the
  comparison is character for character and includes the final newline. A refusal nobody
  caused — an `expected` that came back from a shell `$(cat layer.conf)`, or from an HTTP
  client that trimmed the body — then explains itself instead of looking like a fault in the
  check. `layerId()` and `current()` are unchanged.

- **Built against LangChain4j 1.20.0** (community modules at `1.20.0-beta30`), up from
  `1.19.0`. Nothing in this library's API changes. One thing reaches your classpath: the
  `langchain4j` aggregate, which core needs for `ChatMemoryProvider`, now also brings
  `io.smallrye.reactive:mutiny-zero` (58 KB). That jar backs the non-blocking AI Service
  methods added upstream in `1.20.0`. It is deliberately not excluded, so you can use them.
  If you manage `org.jspecify:jspecify` yourself, note that `langchain4j-core` moved to
  `1.0.1` while Guava — which the GLM module pulls in — still brings `1.0.0`; this project
  pins `1.0.1`.

### Fixed

- **`watch(true)` now works with layers given to `sources(...)`.** It watches the layers that
  are files — `ConfigSource.ofFile(...)` and `ConfigSource.ofWritableFile(...)` — and ignores
  the others; it still refuses to start when no layer is a file at all, and the message now
  names the layers instead of the builder method. Before, `sources(...)` made `watch(true)`
  throw even when every layer was a file, so an application could not both `store` a layer and
  watch for hand edits without building a `FileChangeNotifier` itself, repeating each path in
  a second list. That workaround still works and is no longer needed
  ([ADR-0050](docs/adr/0050-watch-the-file-layers-whichever-method-supplied-them.md)).
- A `ProviderFactory` returning `null` where the SPI declares `Optional` failed with a bare
  `NullPointerException` instead of a configuration error, for
  `createTokenCountEstimator` only — `createStreamingChatModel` and `createModerationModel`
  were already checked. All three now report the block and the provider. This affects
  factories outside this repository; the four shipped here all return an `Optional`.
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
