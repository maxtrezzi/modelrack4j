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

**Status:** Not started · **Entry:** M0

- HOCON loading with explicit layer ordering, merged before resolution — the trap in
  [ADR-0007](../adr/0007-layered-hocon-via-typesafe-config.md), with its own regression
  test.
- `LlmConfig` records with validation in the constructor or loader, so invalid
  configuration is unrepresentable ([ADR-0008](../adr/0008-fail-fast-validation-staged-build-atomic-swap.md)).
- Registry that loads once, no watching, no thread.
- `FakeProviderFactory` in test scope, and the core test suite green with no network and no
  API keys.

---

### M2 — OpenAI and Anthropic

**Status:** Not started · **Entry:** M1

- `modelrack4j-provider-openai` and `modelrack4j-provider-anthropic`, each registering
  through `ServiceLoader` ([ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md)).
- `validate()` in each, enforcing the capability facts confirmed by
  [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix).
- Build-only tests: configuration in, correctly parameterised model object out.
- Integration tests behind a Maven profile, skipped by default, keys from the environment.
- Examples module runs the three-model scenario against real APIs.

---

### M3 — Hot reload

**Status:** Not started · **Entry:** M2, and
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

---

### M4 — Gemini and GLM

**Status:** Not started · **Entry:** M2 (see ordering note)

- Gemini module per [Task 0.4](phase-0-verification.md#task-04--which-gemini-module).
- GLM module per [Task 0.3](phase-0-verification.md#task-03--glm-module-status) and
  decision [D1](open-decisions.md#d1--glm-route-if-no-maintained-module-exists).
- Provider capability matrix in the README, from Task 0.6.

> **Open ordering question.** M4 depends on M2, not on M3. If the first consuming
> application needs genuine cross-*vendor* diversity — several models from different
> vendors cooperating — it needs M4, and it may not need hot reload at all. Confirm with
> the owner whether M4 should be pulled ahead of M3.

---

### M5 — Release readiness

**Status:** Not started · **Entry:** M3 and M4

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
