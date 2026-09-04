# Milestones

**v1 is done at M5, and `0.1.0` is on Maven Central since M6.** Publishing was kept a separate
milestone on purpose, triggered by the library proving itself in the owner's first real project
rather than by a date; the trigger fired on 2026-09-02. The v2 hot-swap wrapper is designed for
but not built
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
core, four providers, BOM, examples — eight Maven projects in the reactor, the aggregating
parent included.

> **Corrected 2026-08-28 by [P14](post-v1.md#p14--a-coherence-pass-over-the-tracked-documentation).**
> This sentence used to list the parent among the seven and so enumerated eight while saying
> seven. The seven are the `<modules>` entries; `mvn` prints `[1/8]` because it counts the
> parent too.

**Verified rather than assumed:**

| Claim | How it was checked |
|---|---|
| `release` 17 ([ADR-0019](../adr/0019-target-java-17.md)) | bytecode major **61** in the built core jar |
| Core takes no provider artifact, and no `opennlp` ([ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md), [ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md)) | `dependency:tree` — core resolves to `langchain4j-core`, the aggregate, `com.typesafe:config`, and nothing else |
| Javadoc and sources jars attach | both present in `target/` alongside the main jar |

**Re-measured 2026-08-25, against `1.19.0`.** The row above asserts the shape of core's
dependency tree but never recorded its size, and an unmeasured cost is what invites a
periodic argument to drop the aggregate exception
([ADR-0020](../adr/0020-core-depends-on-langchain4j-aggregate.md) says not to, and the
number is why). `mvn dependency:tree -pl modelrack4j-core` puts core's whole compile scope
at **eight artifacts**: `langchain4j-core` with its three Jackson jars and `jspecify`,
`com.typesafe:config`, `slf4j-api`, and the aggregate.

> **Corrected 2026-08-28 by [P14](post-v1.md#p14--a-coherence-pass-over-the-tracked-documentation).**
> This read "six artifacts" while enumerating eight, in this entry and in
> [P7](post-v1.md#p7--closing-out-the-outside-review-of-the-public-repository), which is
> where the figure was first written. Six is the count in the *next* paragraph — the
> dependencies the aggregate declares — and it appears to have been carried up one paragraph
> by hand. The tree is re-run above; nothing about the conclusion changes, since the
> argument rests on the aggregate adding one jar and no transitive dependency.

**The aggregate adds 317 KB and no new transitive dependency at all.** It declares six of
its own, and every one is either already present or excluded — `langchain4j-core` and
`slf4j-api` are direct dependencies of core, the three Jacksons arrive through
`langchain4j-core`, and `opennlp-tools` is the ADR-0020 exclusion, which keeps out 1.33 MB.
So the exception ADR-0020 argues for costs one jar. Re-run the command on every LangChain4j
bump: this is a fact about `1.19.0`, and a dependency added upstream would land here
silently.

> **Re-measured 2026-09-04 by [P34](post-v1.md#p34--langchain4j-1200-and-the-jar-the-aggregate-started-bringing),
> and the silent landing happened.** At `1.20.0` the aggregate declares **seven**
> dependencies rather than six, and the new one — `io.smallrye.reactive:mutiny-zero` 1.3.1,
> 58 KB — is not already present, so core's compile scope is **nine artifacts** rather than
> eight and the aggregate now costs two jars. Its own jar grew from 317 KB to 389 KB.
> mutiny-zero's only compile dependency is `jspecify`, which `langchain4j-core` already
> brings, so the tree gains one node and no subtree. The exclusion question was asked and
> answered no: opennlp backs a splitter ADR-0003 puts out of scope, mutiny-zero backs the
> reactive `AiServices` path, and using `AiServices` is something this library leaves
> available on purpose. Nothing about ADR-0020's argument changes; the number it rests on
> does.

One wording note while re-reading the row: "and nothing else" was accurate at M0 and reads
as of M0. `slf4j-api` became a *declared* dependency at M1
([ADR-0028](../adr/0028-core-logs-through-slf4j-api.md)) — it was already present
transitively, so the tree did not grow, but the declared list did.

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

**Corrected 2026-08-24 by [P6](post-v1.md#p6--the-integration-tests-against-live-apis).** The
live call was made, and `gemini-2.5-flash` had been retired upstream — this paragraph named
the one string that could rot unseen, and it was the one that did. It is now
`gemini-3.6-flash`. The GLM half of the claim no longer holds either: `glm-4.6` is now
`glm-5.3`, which `ChatCompletionModel` does not contain, so **both** IDs are outside upstream's
enums and verifiable only by a live call. Nothing in `src/main` changed; `GlmProviderFactory`
always passed the model name through as a raw `String`.

**Both integration-test guards were re-verified with four modules present**, because the
second one is the one that breaks quietly: `mvn -Pintegration verify` with no keys set runs
failsafe and **skips all four ITs**, build passing. Running the profile with one provider
configured therefore skips the other three rather than failing.

**Still never run against a live API.** Adding two more providers to the profile does not
change that. Worth doing once before M5 closes v1.

**Done 2026-08-24, after M5 rather than before it, as
[P6](post-v1.md#p6--the-integration-tests-against-live-apis).** All four providers answered a
real request in one run of `mvn -Pintegration verify`, with `Skipped: 0` on every one. Three
of the four failed first, each time for the provider's own reasons and never inside this
library; one of those failures produced [ADR-0033](../adr/0033-provider-exceptions-pass-through-untranslated.md).

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
than assumed: the five modules that produce a jar — core and the four providers — install
with `-sources` and `-javadoc` jars attached, the parent and the BOM install as POMs, and
`modelrack4j-examples` installs nothing at all — it skips both `install` and
`deploy`, which is what "deliberately NOT published" in its POM description has to mean to
be true.

> **Corrected 2026-08-28 by [P14](post-v1.md#p14--a-coherence-pass-over-the-tracked-documentation),**
> **marker added by [P15](post-v1.md#p15--a-second-coherence-pass-and-what-the-first-one-missed).**
> This read "all six publishable modules install with `-sources` and `-javadoc` jars". Five
> modules produce a jar — core and the four providers — and seven artifacts install, since
> the parent and the BOM install as POMs. P14 fixed the sentence and recorded that it had
> marked all three of its miscounts; this is the one that went unmarked.

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

— which was the README's own bundle table on 2026-08-23, produced by the README's own
configuration. The model identifiers have moved on twice since
([P6](post-v1.md#p6--the-integration-tests-against-live-apis),
[P12](post-v1.md#p12--testing-the-examples-by-hand-and-a-live-break-in-anthropics-sampling-parameters)),
so this is a capture of that day rather than of the current README.

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

---

### M6 — GPG signing and Central Portal publishing

**Status:** Done 2026-09-02 — `0.1.0` published to Maven Central ·
**Branch:** `milestone/m6-central-publishing` · **Entry:** M5, plus the trigger below

**The trigger fired.** M6 had no entry until now, deliberately: it was unscheduled, and its
condition was the library proving itself in the owner's first real project rather than a date.
On 2026-09-02 the owner reported using it in that application and finding that hot reload works
there. That is the whole condition, and it is why the version bump — the step the M6
preparation deliberately held back on 2026-08-31 — is part of this milestone rather than of the
preparation.

**What the trigger actually establishes, stated narrowly.** The library ran inside a real
application, not only inside its own tests and examples, and the feature the whole design is
built around — a configuration file edited while the program runs, picked up without a restart
— behaved as documented there. That is the one thing this repository could never check for
itself: every reload test here drives a registry the test itself created, and every example is
code written to demonstrate the library rather than code that needed it. Nothing else was
reported: no measurement, no defect, and no exercise of the parts an application reaches only
later — `store()`, a non-file `ConfigSource`, moderation, or a multi-model snapshot under load.
This is the owner's report of their own use, recorded as such, and it is not evidence about
those.

On 2026-08-31 the argument for waiting was that a release before the consumer application ran
would record the API changes that application demanded as published versions rather than as
commits. The application has now run. What it has not yet done is ask for a change.

**The account half was already done, on 2026-08-30/31, and none of it is in the repository:**
a Central Portal account created with the GitHub login, which provisioned the namespace
`io.github.maxtrezzi` automatically; an RSA 4096 signing key published to two keyservers; a
user token in `~/.m2/settings.xml`; and Maven 3.9.16 through SDKMAN, because the publishing
plugin requires 3.9.2 and this machine's `/usr/bin/mvn` is 3.8.7.

The decisions are in
[ADR-0045](../adr/0045-publish-through-the-central-portal-from-a-release-profile.md).

#### Built

- A **`release` profile** on the parent POM: `maven-gpg-plugin` 3.2.8 signing at `verify`,
  `central-publishing-maven-plugin` 0.11.0 with `extensions=true`, `publishingServerId=central`
  and `autoPublish=false`, and a profile-scoped `requireMavenVersion` of `[3.9.2,)`. An
  ordinary `mvn clean install` is untouched and still needs no key.
- **`skipPublishing` in `modelrack4j-examples`**, beside the `maven.deploy.skip` it does not
  replace. Both are needed and the POM now says why.
- **The url and scm inheritance attributes** on the parent, so a child does not publish a POM
  whose `url` ends in the module directory.
- **`build/check-release-bundle.sh`**, which reads a built bundle and asserts two things a
  comment cannot: no `modelrack4j-examples` entry, and a `.asc` beside every `.jar` and
  `.pom`.
- **The version bumped to `0.1.0`** across the eight POMs, `project.build.outputTimestamp`
  moved to the release date, the CHANGELOG dated, and the README and manual switched from
  "build and install locally" to the Central coordinates.

#### Found

**Maven 3.9.16 builds this project.** That was open since 2026-08-31, because verifying it
would have written into `target/` while another session was working. `mvn clean install`:
BUILD SUCCESS, 170 tests, 23 s.

**The planned dry run would have proved nothing, and the first attempt at it reached Central
for real.** Two separate defects in the plan, both found by running it:

1. `-DskipPublishing=true` is not a "build the bundle but upload nothing" switch. The flag is
   read **per artifact**, inside the filter that decides what enters the bundle
   (`PublishMojo.lambda$processRelease$1` → `isSkipPublishing`), so it produces an *empty*
   bundle — and the assertion "the bundle holds no `modelrack4j-examples`" would have passed
   against nothing at all.
2. Blocking `centralBaseUrl` does not block a snapshot. With the version still at
   `0.1.0-SNAPSHOT` the plugin took its snapshot path, which deploys straight to
   `https://central.sonatype.com/repository/maven-snapshots/` — an address that comes from
   `centralSnapshotsUrl`, a different parameter. The build therefore made a real authenticated
   request to Central. It was refused with **403 Forbidden**: snapshot publishing is enabled
   per namespace and `io.github.maxtrezzi` does not have it. Nothing was uploaded and no
   deployment exists on the Portal — the snapshot path is an ordinary Maven repository
   transfer, not the publisher API. The lesson is in ADR-0045's consequences, and the working
   dry run blocks **both** URLs.

That 403 also answers, without asking anyone, the middle path the M6 preparation had left
open: publishing `0.1.0-SNAPSHOT` to Central so a consumer application could depend on it
without committing to anything is not available today. It would need snapshot publishing
enabled for the namespace first.

**`child.project.url.inherit.append.path` does not go where the preparation put it.** It was
written on `<url>`, following the shape of the three `child.scm.*` attributes, which do belong
on `<scm>`. Maven accepted it in silence and ignored it: `mvn -pl modelrack4j-core
help:effective-pom` still reported
`https://github.com/maxtrezzi/modelrack4j/modelrack4j-core`. The XSD says why — the attribute
is declared on the **`Model`** type, so it belongs on `<project>`, while the `child.scm.*`
ones are declared on `Scm`. Reproduced in a two-POM project outside this repository, fixed,
and re-checked: core, GLM and the BOM now all report the parent url unchanged. **A POM that
reads correct is not evidence here** — only the effective POM is.

#### Verified

On the machine this repository measures on (AMD Ryzen 7 7840HS, Pop!_OS 24.04, Temurin 25),
with Maven 3.9.16:

- `mvn clean install` — BUILD SUCCESS, 170 tests, no key needed.
- A dry run on the release path with both Central URLs pointed at `127.0.0.1:1`:
  `central-bundle.zip` written, upload refused, build failed at the upload as intended.
- The bundle holds **7 modules** — the parent and the BOM as POMs, core and the four providers
  with a jar, a `-sources` jar and a `-javadoc` jar each — in **110 files**, and **zero**
  paths naming `modelrack4j-examples`.
- `build/check-release-bundle.sh` on that bundle: the exclusion check passes, and the
  signature check fails on all 22 `.jar`/`.pom` artifacts, which is correct for a bundle built
  with `-Dgpg.skip=true`.
- **The same dry run signed**, with the owner typing the passphrase into the pinentry dialog:
  `maven-gpg-plugin` signed 26 files across the eight modules, the upload failed at the blocked
  address as intended, and the script printed *"bundle looks publishable"* — the exclusion
  check and the signature check both green. One signature was then checked rather than
  counted: `gpg --verify` on `modelrack4j-core-0.1.0.jar.asc` reports *Good signature* from
  key `B9602C495E92406FF5DF24A9336FF7186A35E877`. The examples module is signed too, and still
  does not enter the bundle.
- Effective `url` and `scm` of three modules, read from `help:effective-pom`.
- The profile-scoped Maven floor, by running `/usr/bin/mvn -Prelease validate` on 3.8.7:
  *"Detected Maven Version: 3.8.7 is not in the allowed range [3.9.2,)"*, from the enforcer
  rather than from the plugin.

#### The release

Run the same evening, in the order the entry had planned. The upload itself is one command;
what is worth recording is what each step actually returned.

`mvn -Prelease clean deploy` uploaded the bundle and Central validated it:
`deploymentId 9b9ce361-f0dc-421c-8539-577f266c20ff`, state `VALIDATED`, `errors {}`,
`warnings []`, holding the seven artifacts and — visible in the log as seven
`Skipping Central Release Publishing for artifact 'modelrack4j-examples' at user's request`
lines — not the examples. `autoPublish=false` did its job: the build stopped there.

The owner pressed **Publish** on the Portal. The deployment was observed in `PUBLISHING` for
**4 minutes 53 seconds** — 21:58:41 to 22:03:34, polled every 20 s — and then reached
`PUBLISHED`. That is a lower bound on how long it took: the first poll already found it
publishing, so the interval is measured from the first observation rather than from the
press. While it was publishing, the status endpoint reported an empty `purls` list and
`errors: {"common": ["Deployment components info not found"]}` — transitional, and gone once
the state settled.

**Verified after publication, not assumed:**

- The Portal's own `published` endpoint: `true` for all seven artifacts, and **`false` for
  `modelrack4j-examples`**, which is the one that matters.
- `repo1.maven.org` serves `modelrack4j-core-0.1.0.jar` and its `.asc` (HTTP 200) and returns
  **404** for `modelrack4j-examples-0.1.0.jar`.
- Resolved into an **empty local repository** with
  `mvn -Dmaven.repo.local=<temp> dependency:get`, so the download came from Central rather
  than from the `~/.m2` the release build had just written.
- The **published** parent POM carries all four inheritance attributes — the fix in this
  milestone, checked where it finally counts rather than in the working tree.
- The published `modelrack4j-provider-anthropic` jar carries its
  `META-INF/services/io.github.maxtrezzi.modelrack4j.spi.ProviderFactory` entry, and
  `modelrack4j-core` carries `META-INF/LICENSE` and `META-INF/NOTICE` stamped
  `2026-09-02 00:00` — the reproducible-build timestamp, not the moment of the build.

The `v0.1.0` tag is created on `main` after the merge, so that it is reachable from the branch
it names: this repository squash-merges, so a tag on the pre-merge commit would sit outside
`main`'s history.

**A signed step needs a human, but not an interactive shell — and this entry first said
otherwise.** The passphrase is not cached, and `gpg --batch --pinentry-mode error` here fails
with `No pinentry`, which was written up as "no automated shell on this machine can supply the
passphrase". That flag orders gpg to fail instead of asking, so the message was produced by
the test rather than found by it. What is actually installed is `pinentry-gnome3`, as the
default `/usr/bin/pinentry`, with `DISPLAY=:0` present: a signing step started from any shell
opens a dialog on the desktop. A flag that changes the behaviour under test cannot be evidence
about that behaviour.

**An off-machine backup of the signing key.** It still sits on the same disk as the key.
