# Milestones

**v1 is done at M5.** Publishing to Maven Central is a separate later milestone (M6),
triggered by the library proving itself in the owner's first real project rather than by a
date. The v2 hot-swap wrapper is designed for but not built
([ADR-0011](../adr/0011-independent-name-and-deferred-wrapper.md)).

---

### M0 — Skeleton and CI

**Status:** Not started · **Entry:** Phase 0 tasks resolved

- Complete the [Phase 0 verification tasks](phase-0-verification.md).
- Multi-module Maven build compiles: parent POM with `dependencyManagement` and plugins,
  plus the core, provider, BOM, and examples modules.
- CI green on the baseline JDK and the latest LTS.
- License header check, dependency-convergence enforcement, and a Javadoc build that
  passes — Central will require javadoc and sources jars later, and retrofitting a clean
  Javadoc build is worse than starting with one.

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
