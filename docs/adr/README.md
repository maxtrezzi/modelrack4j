# Architecture Decision Records

One file per decision, numbered sequentially, never deleted. A decision that turns out
wrong is not edited away — a new ADR supersedes it and both stay in the history.

## Format

`NNNN-kebab-case-title.md`, starting at `0001`. Copy `0000-template.md`.

The body follows a **Context → Forces → Decision → Consequences** shape, extending the
Forces/Decision/Consequences form already used by the project's decision records: what
was being decided and why then, what pressures made it a real choice, what was chosen, and
what the project now lives with. Consequences include the costs, not only the benefits —
an ADR that lists no downside was not a decision.

The header carries `Supersedes` (this ADR replaces one wholesale) and `Amends` (this ADR
narrows or widens part of one). Both default to `—`.

## Status values

| Status | Meaning |
|---|---|
| `Proposed` | Written up, not yet agreed |
| `Accepted` | In force; implementation must follow it |
| `Accepted — <aspect> amended by ADR-NNNN` | Still in force, but a later ADR narrowed or widened part of it; the later ADR wins where they differ |
| `Superseded by ADR-NNNN` | Replaced wholesale; kept for the reasoning trail |

Superseding or amending an ADR means editing only the old file's `Status` line and adding
the pointer. Its body stays untouched — including the parts the newer ADR overrode, since
those are what make the change legible.

## When to write one

Whenever a discussion settles something that constrains future code: a dependency taken
on, an API shape fixed, a scope boundary drawn, a mechanism chosen over an alternative
that was genuinely considered. Not for reversible implementation details, and not for
things the code states plainly on its own.

Discussion transcripts and half-formed thinking do **not** belong here — those go to
`brainstorm/discussions/`, which is local-only and never committed. An ADR is the
distilled, rewritten result, safe to publish.

Work items do not belong here either. What to do, and whether it is done, lives in
[`../tasks/`](../tasks/README.md) (ADR-0015); an ADR explains why the work is shaped the
way it is.

## Index

ADR-0002 through ADR-0014 were backfilled on 2026-07-26 from the decision records in the
project's planning document; the decisions themselves predate the records and were made
before implementation began. They are grouped below by what they govern rather than listed
by number, since the numbering only reflects the order of the original appendix.

**Scope and positioning**

| ADR | Title | Status |
|---|---|---|
| [0002](0002-scope-to-langchain4j-llm-configuration.md) | Scope to LangChain4j LLM configuration, not generic reloadable config | Accepted |
| [0003](0003-bundle-holds-config-shaped-inputs-only.md) | A bundle holds the config-shaped inputs only | Accepted |
| [0011](0011-independent-name-and-deferred-wrapper.md) | Independent name; the hot-swap wrapper deferred to v2 | Accepted |
| [0017](0017-apache-2-0-license.md) | License under Apache 2.0, with no NOTICE file | Accepted |
| [0025](0025-fix-coordinates-under-io-github-maxtrezzi.md) | Fix the coordinates at `io.github.maxtrezzi:modelrack4j-*` | Accepted |

**Structure and configuration**

| ADR | Title | Status |
|---|---|---|
| [0005](0005-provider-factory-spi-via-serviceloader.md) | Abstract Factory per provider, discovered via `ServiceLoader` | Accepted — core's dependency set amended by ADR-0020 |
| [0020](0020-core-depends-on-langchain4j-aggregate.md) | Core also depends on the `langchain4j` aggregate, for `ChatMemoryProvider` | Accepted — dependency set amended by ADR-0028 |
| [0028](0028-core-logs-through-slf4j-api.md) | The watcher logs, and core takes slf4j-api to do it | Accepted |
| [0007](0007-layered-hocon-via-typesafe-config.md) | Layered HOCON via Typesafe Config as a core dependency | Accepted |
| [0006](0006-named-configurations-with-per-name-diffing.md) | Named configurations, one merged snapshot, per-name diffing | Accepted |
| [0010](0010-discriminators-only-with-two-real-variants.md) | Discriminated variants only where two real variants exist today | Accepted — estimator-availability premise amended by ADR-0021 |
| [0004](0004-expose-chatmemoryprovider.md) | Expose `ChatMemoryProvider`, not a bare `ChatMemory` | Accepted — estimator-availability premise amended by ADR-0021 |
| [0021](0021-token-estimation-is-universal-but-two-cost-classes.md) | Token estimation is universal; the capability that varies is its cost | Accepted |
| [0027](0027-remote-token-counting-is-opt-in.md) | Token-window memory on a remote estimator is opt-in | Accepted |
| [0030](0030-one-timeout-in-the-schema.md) | One `timeout` in the schema; providers map it onto their own client | Accepted |
| [0018](0018-manage-langchain4j-versions-via-bom.md) | Manage LangChain4j versions by importing its BOM | Accepted — one import becomes two, per ADR-0022 |
| [0022](0022-glm-via-the-community-module-and-its-bom.md) | Take GLM from the community module, and import its BOM alongside the main one | Accepted |
| [0023](0023-gemini-via-the-stable-google-ai-gemini-module.md) | Take Gemini from `langchain4j-google-ai-gemini`, the stable module | Accepted |
| [0019](0019-target-java-17.md) | Target Java 17, build on a newer JDK | Accepted — CI matrix amended by ADR-0026 |
| [0026](0026-ci-matrix-is-floor-dev-jdk-and-current-lts.md) | The CI matrix is the floor, the development JDK, and the current LTS | Accepted |

**Reload semantics**

| ADR | Title | Status |
|---|---|---|
| [0008](0008-fail-fast-validation-staged-build-atomic-swap.md) | Fail-fast validation, staged build, atomic swap | Accepted — swap scope widened by ADR-0012 |
| [0012](0012-reload-atomicity-is-snapshot-wide.md) | Reload atomicity is snapshot-wide, not per-bundle | Accepted |
| [0013](0013-watch-directories-resolve-symlinks.md) | Watch directories, resolve symlinks, document the macOS caveat | Accepted — symlink strategy and filename filter amended by ADR-0024 |
| [0024](0024-watch-the-symlink-s-directory-not-its-real-path.md) | Watch the symlink's own directory; filename filtering cannot gate a ConfigMap swap | Accepted |
| [0014](0014-lifecycle-of-removed-names-and-superseded-bundles.md) | Lifecycle of removed names and superseded bundles | Accepted |
| [0009](0009-holder-api-primary-listeners-optional.md) | Holder API primary, listeners optional | Accepted |
| [0029](0029-reload-callbacks-are-quiet-contained-and-not-a-heartbeat.md) | A reload callback means something changed — nothing else | Accepted |

**Process**

| ADR | Title | Status |
|---|---|---|
| [0001](0001-record-decisions-as-adrs.md) | Record decisions as ADRs; keep discussion logs out of the repo | Accepted |
| [0015](0015-track-work-items-in-docs-tasks.md) | Track work items in `docs/tasks/`, alongside the ADRs | Accepted |
| [0016](0016-one-feature-branch-per-task.md) | One feature branch per task | Accepted |
