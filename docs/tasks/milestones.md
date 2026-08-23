# Milestones

**v1 is done at M5.** Publishing to Maven Central is a separate later milestone (M6),
triggered by the library proving itself in the owner's first real project rather than by a
date. The v2 hot-swap wrapper is designed for but not built
([ADR-0011](../adr/0011-independent-name-and-deferred-wrapper.md)).

---

### M0 — Skeleton and CI

**Status:** Done 2026-08-20 · **Entry:** Phase 0 tasks resolved

- Complete the [Phase 0 verification tasks](phase-0-verification.md).
- Multi-module Maven build compiles: parent POM with `dependencyManagement` and plugins,
  plus the core, provider, BOM, and examples modules.
- CI green on the baseline JDK and the latest LTS.
- License header check, dependency-convergence enforcement, and a Javadoc build that
  passes — Central will require javadoc and sources jars later, and retrofitting a clean
  Javadoc build is worse than starting with one.

#### Built

`mvn clean verify` is green on **JDK 21 and 25 locally**; 17 is covered by CI
([ADR-0026](../adr/0026-ci-matrix-is-floor-dev-jdk-and-current-lts.md)). Seven modules per
the plan's layout, at `io.github.maxtrezzi:modelrack4j-*`
([ADR-0025](../adr/0025-fix-coordinates-under-io-github-maxtrezzi.md)):
parent, core, four providers, BOM, examples.

**Verified rather than assumed:**

| Claim | How it was checked |
|---|---|
| `release` 17 ([ADR-0019](../adr/0019-target-java-17.md)) | bytecode major **61** in the built core jar |
| Core takes no provider artifact, and no `opennlp` ([ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md), [ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md)) | `dependency:tree` — core resolves to `langchain4j-core`, the aggregate, `com.typesafe:config`, and nothing else |
| Javadoc and sources jars attach | both present in `target/` alongside the main jar |

**Three things the build caught that reasoning had not:**

1. **The GLM module breaks dependency convergence.** Its `jjwt` pulls `jjwt-jackson`, which
   pulls `jackson-databind` 2.12.7.1 against LangChain4j's 2.22.1. Nearest-wins happens to
   pick 2.22.1 today, which is exactly why the rule is on — the outcome is depth-dependent
   and would flip silently. Resolved with an exclusion local to the GLM module, so Jackson
   versions still come only from the BOM and still move on a bump. This is the concrete form
   of the weight [ADR-0022](../adr/0022-glm-via-the-community-module-and-its-bom.md) warned
   about.
2. **The license check scanned `brainstorm/`.** The root module's basedir is the repository
   root, so the check found the local-only spike file — which exists on this machine and
   never in CI, meaning the build would have behaved differently in the two places.
   Explicitly excluded.
3. **`@implNote` is a hard javadoc error outside the JDK.** `@apiNote`/`@implSpec`/`@implNote`
   are only recognised implicitly when javadoc documents the JDK itself; elsewhere they must
   be registered as custom tags. Registered project-wide, so the first Javadoc-carrying class
   did not have to discover it.

**Deliberately minimal source.** Provider and example modules have no sources yet — they are
empty until M2/M4, and a module holding only a `package-info` makes Javadoc fail rather than
skip. Core carries `UnknownConfigurationException`, whose contract
[ADR-0014](../adr/0014-lifecycle-of-removed-names-and-superseded-bundles.md) already fixed,
so the compile → javadoc → license → package pipeline is exercised by a real type rather than
a placeholder. The SPI signature is M1's to define and is **not** pre-empted here.

**Open, flagged rather than decided:** the copyright owner string in the file headers and
POM metadata is `maxtrezzi`, taken from the repository's git identity.
[ADR-0017](../adr/0017-apache-2-0-license.md) deliberately did not settle it. If a different
legal name should appear, it is a find-and-replace across the headers, the header template
and the POM `<developers>` block.

---

### M1 — Core without watching

**Status:** Done 2026-08-21 · **Entry:** M0

- HOCON loading with explicit layer ordering, merged before resolution — the trap in
  [ADR-0007](../adr/0007-layered-hocon-via-typesafe-config.md), with its own regression
  test.
- `LlmConfig` records with validation in the constructor or loader, so invalid
  configuration is unrepresentable ([ADR-0008](../adr/0008-fail-fast-validation-staged-build-atomic-swap.md)).
- Registry that loads once, no watching, no thread.
- `FakeProviderFactory` in test scope, and the core test suite green with no network and no
  API keys.

#### Built

**37 tests, green on JDK 21 and 25, with no network and no API keys.**

| Class | Tests | Covers |
|---|---|---|
| `LayeredResolutionTest` | 6 | the [ADR-0007](../adr/0007-layered-hocon-via-typesafe-config.md) trap, and UTF-8 decoding |
| `LlmConfigTest` | 13 | record validation and value equality |
| `LlmRegistryTest` | 18 | build, lookup, discovery, capability rules, SPI misbehaviour |

The count grew after M1 was first written: a review pass against the Java 17 profile and a
full review against the plugin added the UTF-8, awkward-name, non-object-block,
capability-not-produced and null-returning-factory cases.

**The layering trap has its own regression suite**, as ADR-0007 required. The load-bearing
case: a defaults layer whose `api-key` is `${MODELRACK4J_ABSENT_VAR}` — an environment
variable deliberately never set — overridden by a customer layer. Merged-then-resolved this
works, because the substitution node is gone by the time anything resolves. Resolved per file
it throws. There is also a cross-layer reference test, where a lower layer refers to a key
only a higher layer defines.

Confirmed while writing it, rather than assumed: `Config.resolve()` **does** fall back to
environment variables under default options, so `${VAR}` reads the environment and fails
loudly when unset. That is what mandatory substitution is for, and one test pins each
direction.

**Invalid configuration is unrepresentable.** `LlmConfig` validates in its compact
constructor, so an instance that exists is valid. `MemoryConfig` is a **sealed interface**
with a record per variant rather than one record carrying unused fields — `max-messages` and
`max-tokens` cannot both be set, because no type has both.

**ADR-0027's rules are implemented in core**, driven by the capability the factory reports, so
no provider module restates them. All five edges have tests, including the one that is easy
to lose: the rejection message must name `allow-remote-token-counting`, or opt-in silently
becomes reject. That assertion is the contract, not decoration.

**`FakeProviderFactory` is three fakes, not one** — `fake-local`, `fake-remote`, `fake-absent`
— mirroring the real capability spread from [ADR-0021](../adr/0021-token-estimation-is-universal-but-two-cost-classes.md)
and ADR-0022, so the capability rules are tested against all three classes rather than one.

**Two deliberate departures from the plan's sketch, both narrow:**

1. **`ProviderFactory` gains `tokenEstimation()`.** The plan's SPI had only
   `createTokenCountEstimator`, which is a two-valued answer. ADR-0021 requires three, and
   ADR-0027's rule cannot be expressed without telling `LOCAL` from `REMOTE`.
2. **No `watch(boolean)` on the builder yet.** M1 has no watcher, and shipping a method that
   throws would be worse than adding it in M3, which is source-compatible.

**Defaults live in `modelrack4j-reference.conf`, not `reference.conf`.** HOCON merges
`reference.conf` automatically only for fixed paths, and configuration names are user-chosen,
so the loader merges the defaults block into each named block explicitly. The file says so at
the top, since the naming looks like a mistake otherwise.

**Not in M1, and not started:** watching and reload (M3), the snapshot/diff machinery
([ADR-0012](../adr/0012-reload-atomicity-is-snapshot-wide.md)), the provenance/`origin()`
debug API, and any real provider — the four provider modules are still empty.

---

### M2 — OpenAI and Anthropic

**Status:** Done 2026-08-23 · **Entry:** M1

- `modelrack4j-provider-openai` and `modelrack4j-provider-anthropic`, each registering
  through `ServiceLoader` ([ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md)).
- `validate()` in each, enforcing the capability facts confirmed by
  [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix).
- Build-only tests: configuration in, correctly parameterised model object out.
- Integration tests behind a Maven profile, skipped by default, keys from the environment.
- Examples module runs the three-model scenario against real APIs.

#### Built

**52 unit tests, green on JDK 21 and 25, all offline with no API keys** — core 37,
`-provider-openai` 7, `-provider-anthropic` 8. LangChain4j builders do not contact the
provider, so a dummy credential is enough to assert real parameterisation.

Both factories register through `ServiceLoader`, and each module has an end-to-end test that
builds a bundle through `LlmRegistry` rather than calling the factory directly — that is the
only thing which actually proves the `META-INF/services` wiring.

**Capabilities are asserted, not trusted.** The [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix)
matrix is now enforced in code and pinned by tests: OpenAI reports `LOCAL` and supplies
moderation; Anthropic reports `REMOTE`, supplies no moderation model, and its `validate()`
rejects a config that enables it. The [ADR-0027](../adr/0027-remote-token-counting-is-opt-in.md)
opt-in rule is exercised against a *real* remote counter for the first time.

**Two provider-specific traps found while wiring the builders:**

1. **OpenAI's moderation endpoint takes its own model, not the chat model.** Forwarding
   `model-name` into `OpenAiModerationModel` would send a request the API rejects, so the
   factory deliberately does not pass it and lets LangChain4j supply the moderation model.
   This is invisible offline — every build-only test would still pass — so it is commented at
   the call site.
2. **Local token counting needs a tokenizer the bundled jtokkit recognises.**
   `new OpenAiTokenCountEstimator("no-such-model")` throws
   `IllegalArgumentException: Model ... is unknown to jtokkit`, verified. Wrapped as a
   `ConfigValidationException` naming the model and pointing at `message-window`, because
   otherwise it surfaces during memory eviction rather than at build time. Practical exposure
   is small — `gpt-4o`, `gpt-4.1`, `gpt-5`, `gpt-5.1`, `o3` and `gpt-4-turbo` all resolve.

**Model names come from LangChain4j's own enums** (`AnthropicChatModelName`,
`OpenAiChatModelName` at 1.19.0), not from recollection: `claude-sonnet-4-6`, `gpt-5-mini`.

**Integration tests are doubly guarded, and both guards were verified:**

| Command | Failsafe | Result |
|---|---|---|
| `mvn verify` | does not run at all | no `failsafe-reports` produced |
| `mvn -Pintegration verify`, no keys | runs | both ITs **skipped**, build passes |

The second guard matters: `@EnabledIfEnvironmentVariable` means running the profile with only
one provider configured skips the other instead of failing.

**The examples module is wired as a consumer would wire it** — it depends on core *and* both
provider modules, because core alone knows no providers. `ThreeModelCouncil` asks the registry
for each bundle at the point of use rather than caching it in a field, which is the habit the
holder API exists to encourage.

**Carried forward to M5's README:** LangChain4j silently ignores moderation on the
`AiServices` streaming path (upstream issue #2779). This library only builds the objects, but
the limitation has to be documented where a user configuring `streaming = true` alongside
`moderation.enabled = true` will see it.

---

### M3 — Hot reload

**Status:** Done 2026-08-23 · **Entry:** M2, and
[Task 0.8](phase-0-verification.md#task-08--watch-strategy-spike) complete

Task 0.8 comes first — the spike exists so this milestone is built on measured behaviour
rather than assumption.

- Directory watching, symlink-aware, re-registering when a watched directory is lost
  ([ADR-0013](../adr/0013-watch-directories-resolve-symlinks.md)).
- Debounce treating CREATE and MODIFY alike.
- Per-name diff by record equality ([ADR-0006](../adr/0006-named-configurations-with-per-name-diffing.md)),
  staged build, and all-or-nothing snapshot swap
  ([ADR-0012](../adr/0012-reload-atomicity-is-snapshot-wide.md)).
- Snapshot-level listeners; names added and removed handled per
  [ADR-0014](../adr/0014-lifecycle-of-removed-names-and-superseded-bundles.md).
- `AutoCloseable` registry stopping a daemon watcher thread cleanly.
- Async test suite green: debounce collapsing rapid writes into one reload,
  temp-file-then-rename, symlink target swap, name added, name removed, and one invalid
  block swapping nothing while firing exactly one failure callback.

#### Built

**69 unit tests, green on JDK 21 and 25, all offline with no API keys** — core 54,
`-provider-openai` 7, `-provider-anthropic` 8. The 17 new ones drive a real `WatchService`
against real files in a `@TempDir`: the behaviours that matter here belong to the filesystem,
and a mock would assert the assumptions instead of testing them.

**Reload is one volatile write.** `SnapshotLoader` either returns a complete map of name to
bundle or throws, so the staging area [ADR-0012](../adr/0012-reload-atomicity-is-snapshot-wide.md)
requires is the natural shape of the code rather than something bolted on. The live snapshot
is a single `volatile Map` field; publishing is one assignment. There is no lock anywhere in
the reload path, because the watcher thread is the only writer.

**The watcher has the two modes ADR-0024 specified, and both are pinned by a test that fails
when the mode is wrong.** Restoring the filename filter for symlinked paths — the "obvious
tidy-up" that ADR-0024 warns about — was tried deliberately: `configMapSymlinkSwapIsSeen`
times out, which is the silent-never-reloads failure reproduced in ten seconds instead of in
production.

**What the reload tests actually hold down:**

| Test | Guards |
|---|---|
| `rapidWritesCollapseIntoOneReload` | five writes, one reload |
| `tempFileThenRenameIsSeen` | the ordinary save, arriving as CREATE, with the `.tmp` events discarded |
| `configMapSymlinkSwapIsSeen` | the three-level layout swapped by atomic rename ([ADR-0024](../adr/0024-watch-the-symlink-s-directory-not-its-real-path.md)) |
| `invalidBlockSwapsNothing` | one bad block holds back a correct edit in the same save; one failure, same bundle instance still live |
| `unchangedBundlesAreCarriedOver` | `isSameAs`, not `isEqualTo` — the untouched bundle is the *same object* ([ADR-0006](../adr/0006-named-configurations-with-per-name-diffing.md)) |
| `getIsSafeDuringReload` | four reader threads across ten reloads never see a bundle whose config belongs to another block |
| `lostDirectoryIsReregistered` | a directory deleted, recreated, and *then* edited again |

**One of those tests passed for the wrong reason and had to be rewritten.**
`lostDirectoryIsReregistered` originally deleted the directory, recreated it with new
content, and asserted the new content arrived. It passed in 105 ms — far too fast to have
waited for the one-second re-registration retry. The deletion's *own* event was enough to
trigger the reload, and by the time the debounce expired the replacement file was already on
disk, so the assertion held without the recovery path ever running. It now edits the file a
second time after the first reload settles, which only succeeds on a live registration; it
takes 1.2 s, and disabling re-registration makes it fail. A test that cannot fail is worse
than no test, because it is counted.

**Two decisions came out of building it**, neither of which the earlier ADRs answered:

1. **[ADR-0028](../adr/0028-core-logs-through-slf4j-api.md)** — the watcher runs on its own
   thread and several of its outcomes have no caller to throw at, so core now declares
   `slf4j-api`. This amends the closed dependency set of
   [ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md); the rule that actually
   matters, **no provider artifact ever**, is untouched. The jar was already there
   transitively — what changed is the declaration and the pin.
2. **[ADR-0029](../adr/0029-reload-callbacks-are-quiet-contained-and-not-a-heartbeat.md)** —
   a callback means something changed. A reload resolving to the identical snapshot swaps
   nothing and notifies nobody, a listener that throws cannot stop future reloads, and a
   recovered directory counts as a change.

**New public API**, all source-compatible additions: `Builder.watch(boolean)` and
`Builder.debounce(Duration)`, `onReload` / `onReloadFailure`, and the two records they carry,
`ReloadChange` and `ReloadFailure`. The debounce knob is deliberate — the 300 ms default is
~100x the event burst measured on Linux, but the figure is a measurement on one platform, and
a slower filesystem should not require a fork.

**Carried forward to M5's README:** the reload latency note can still quote the Linux figure
only. Task 0.8's macOS measurement is unchanged by this milestone — it qualifies the
documentation, not the design.

---

### M4 — Gemini and GLM

**Status:** Done 2026-08-23 · **Entry:** M2

- Gemini module per [Task 0.4](phase-0-verification.md#task-04--which-gemini-module).
- GLM module per [Task 0.3](phase-0-verification.md#task-03--glm-module-status) and
  decision [D1](open-decisions.md#d1--glm-route-if-no-maintained-module-exists).
- Provider capability matrix in the README, from Task 0.6.

> **Ordering question, now closed.** M4 depended on M2, not on M3, and the entry asked
> whether it should be pulled ahead. It was not: M3 shipped first, and M4 followed
> immediately, so no consuming application waited on either.

#### Built

**87 unit tests, green on JDK 21 and 25, all offline with no API keys** — core 54,
`-provider-openai` 7, `-provider-anthropic` 8, `-provider-gemini` 8, `-provider-glm` 10. All
four provider modules now register through `ServiceLoader`, and each has an end-to-end test
that builds a bundle through `LlmRegistry` rather than calling its factory directly — the
only thing that actually proves the `META-INF/services` wiring.

**Every capability claim was read out of the artifact before any code was written**, by
listing the jars' classes. That is how the two gaps below were found rather than discovered
at runtime:

| Provider | Chat | Streaming | Moderation | Token estimation |
|---|---|---|---|---|
| `openai` | ✅ | ✅ | ✅ | **local** |
| `anthropic` | ✅ | ✅ | ❌ | remote |
| `gemini` | ✅ | ✅ | ❌ | remote |
| `glm` | ✅ | ✅ | ❌ | **none** |

**GLM is the first provider that makes `TokenEstimation.ABSENT` a live value.** Until now
ABSENT existed for a case no real provider occupied. Token-window memory is *unavailable*
there rather than expensive, so [ADR-0027](../adr/0027-remote-token-counting-is-opt-in.md)'s
opt-in flag must not smuggle it through — the flag covers a cost, not an absence.
`tokenWindowIsRefusedOutright` sets the flag and asserts the rejection anyway.

**Three GLM-specific traps, all found by reading the artifact:**

1. **The builder key is `model`, not `modelName`.** The schema's `model-name` maps across in
   the factory. A test asserts the name actually arrived, because a wrong builder key
   compiles fine and produces a model configured with the provider's default.
2. **There is no single request timeout** — four separate ones, and two of them,
   `callTimeout` and `writeTimeout`, are deprecated and marked for removal. Settled as
   [ADR-0030](../adr/0030-one-timeout-in-the-schema.md): the schema keeps one `timeout` key
   and each provider maps it, so GLM applies it to the two that are not deprecated. GLM
   consequently has no whole-call bound, which the ADR records as an accepted cost.
3. **`ZhipuAiChatModel.provider()` returns `ModelProvider.OTHER`.** An application routing on
   `ChatModel.provider()` cannot tell GLM from any other community module, so its test
   asserts on the model class and the resolved model name instead. Pinned so the day it
   changes upstream is visible.

**Gemini was the straightforward one** and needed no such handling: the builder shape matches
OpenAI's and Anthropic's exactly, and the stable module choice from
[ADR-0023](../adr/0023-gemini-via-the-stable-google-ai-gemini-module.md) held.

**One honest gap.** M2's entry recorded that model names came from LangChain4j's own enums
rather than from recollection. That is still true for GLM — `glm-4.6` is from
`ChatCompletionModel` — but **`langchain4j-google-ai-gemini` ships no model-name enum**, so
`gemini-2.5-flash` could not be checked against the artifact. It is the one string in this
milestone that only a live call can verify, and the IT says so at the point of use.

**Both integration-test guards were re-verified with four modules present**, because the
second one is the one that breaks quietly: `mvn -Pintegration verify` with no keys set runs
failsafe and **skips all four ITs**, build passing. Running the profile with one provider
configured therefore skips the other three rather than failing.

**Still never run against a live API.** Adding two more providers to the profile does not
change that. Worth doing once before M5 closes v1.

**Deferred into M5 with the README that holds it:** M4's third bullet asks for the capability
matrix in the README, and there is no README yet — M5 creates it. The matrix itself is done
and lives in [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix), whose
GLM row this milestone finally filled in; M5 renders it rather than re-deriving it.

---

### M5 — Release readiness

**Status:** Done 2026-08-23 · **Entry:** M3 and M4

Everything needed to publish, without publishing.

- Apache 2.0 license headers throughout.
- Sources and javadoc jars building cleanly.
- Full POM metadata: license, SCM, developers, description, url.
- README complete: unofficial positioning, quick start with the three-model scenario, the
  don't-cache-bundles warning ([ADR-0009](../adr/0009-holder-api-primary-listeners-optional.md)),
  the layering example, the provider capability matrix, and the documented reload latency
  including the macOS figure.
- `CHANGELOG.md` in Keep a Changelog format. SemVer, 0.x until the API settles.
- `mvn install` produces consumable `0.1.0-SNAPSHOT` artifacts.

**v1 is done here.** M6 — GPG signing, Central Portal publishing, namespace verification —
is deliberately separate and not scheduled.

#### Built

**Most of this milestone was already done, and the useful part was finding out which.** M0
set up the POM metadata, the license-header check, and the sources and javadoc jars, because
retrofitting a clean Javadoc build is worse than starting with one. Re-checked here rather
than assumed: all six publishable modules install with `-sources` and `-javadoc` jars
attached, and `modelrack4j-examples` installs nothing at all — it skips both `install` and
`deploy`, which is what "deliberately NOT published" in its POM description has to mean to
be true.

So M5 came down to the two documents that did not exist, plus one behaviour the act of
writing them exposed.

**The README is verified, not drafted.** Its quick start was compiled and run from a
throwaway consumer project **outside the reactor**, resolving `0.1.0-SNAPSHOT` from `~/.m2`
through the BOM import exactly as the README tells a reader to. That is the only thing which
actually proves the installed artifacts are consumable, and it exercises `ServiceLoader`
discovery from a plain classpath rather than from a Maven module. It printed:

```
names: [CR, SH, SL]
CR: provider=openai    model=gpt-5-mini          streaming=false moderation=true  memory=false
SH: provider=anthropic model=claude-sonnet-4-6   streaming=true  moderation=false memory=false
SL: provider=anthropic model=claude-sonnet-4-6   streaming=false moderation=false memory=true
```

— which is the README's own bundle table, produced by the README's own configuration.

**Writing the reload section found a silent failure mode, now fixed
([ADR-0031](../adr/0031-a-rejected-reload-is-always-logged.md)).** With `watch(true)` and no
`onReloadFailure` listener, a broken config file produced *no output of any kind*. The
rejection was correct and the previous snapshot stayed live — but every later edit to that
file then failed the same way, so the user's symptom was "reloading stopped working", with
nothing to read. `reload()` now logs the rejection at WARN with its cause, unconditionally.
Confirmed the same way as the quick start, from the consumer project with a real SLF4J
binding and no listener registered: the WARN line appears, the stack trace with it, and
`get("SL")` still returns the previous model. [ADR-0029](../adr/0029-reload-callbacks-are-quiet-contained-and-not-a-heartbeat.md)
is amended rather than superseded — what a *callback* means is unchanged.

**The `CHANGELOG` is Keep a Changelog with one addition**: an explicit pre-1.0 note that a
breaking change may land in a `0.x` minor, since the alternative is a reader assuming SemVer
guarantees the project has not made yet.

**Two things the README states as gaps rather than smoothing over:**

1. **macOS latency is still unmeasured**, so the latency section gives the Linux figures,
   says the macOS implementation is polling-based, and tells the reader to measure it
   themselves rather than quoting a number from hearsay. This was M5's one bullet that could
   not be closed; [Task 0.8](phase-0-verification.md#task-08--watch-strategy-spike) stays
   *Partly done* and keeps it.
2. **`-Pintegration` has still never reached a live API.** Both guards are verified across
   all four providers, and the payload has never run.

**Not done here, deliberately:** anything that publishes. No GPG plugin, no Central Portal
configuration, no `deploy` execution — that is M6, and it is unscheduled by design.
