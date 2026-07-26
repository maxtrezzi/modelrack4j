# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

**Greenfield — no code exists yet.** The repository contains documentation only:
`CLAUDE.md`, `docs/`, `.gitignore`, and the untracked `brainstorm/`. There is no POM and no
source tree. Git is initialised on `main` with no remote configured.

Three documents matter, with different jobs:

- **`docs/tasks/`** — what to do next and whether it is done: the Phase 0 verification
  tasks, milestones M0–M5, and the decisions waiting on the owner. **Start here.**
- **`docs/adr/`** — why the work is shaped this way: ADR-0002 … ADR-0014 carry the
  reasoning behind each design constraint, backfilled from the plan's Appendix A.
- **`brainstorm/PLAN.md`** — the specification: config schema, target API shape, SPI
  signature, module layout, and the §2 decision table. Local-only, never committed. Read it
  before writing code and follow it rather than re-deriving a design.

They overlap deliberately. Where they differ: the ADR wins on a *decision*, `docs/tasks/`
wins on *status*, and the plan remains the owner's working copy and the only home for the
schema and API detail the tracked files do not restate.

**`brainstorm/` is local-only and MUST NEVER be committed.** It is git-ignored; never
`git add -f` it, never quote it into a commit message, README, issue, or PR body, and
never copy its contents into a tracked file. Anything from the plan that consumers need
must be rewritten for its destination (README, Javadoc, CHANGELOG), not pasted.

## Decision workflow — follow this every session

Three artifacts, different audiences (ADR-0001, ADR-0015):

- **`brainstorm/discussions/YYYY-MM-DD-topic.md`** — local-only, never committed. Log
  every substantive design discussion here: what was asked, what was weighed, what was
  rejected and why, what is still open. Write it at the end of the discussion, not from
  memory three sessions later.
- **`docs/adr/NNNN-title.md`** — tracked and publishable. Whenever a discussion *settles*
  something that constrains future code — a dependency taken on, an API shape fixed, a
  scope boundary drawn, a mechanism chosen over a real alternative — write an ADR. Copy
  `docs/adr/0000-template.md`, take the next number, follow
  Context → Forces → Decision → Consequences, and add a row to the index in
  `docs/adr/README.md`.
- **`docs/tasks/`** — tracked. Update the status of whatever you worked on, in the entry
  and in the status board, in the same commit as the work. Verification tasks record what
  was *found*, not just that they finished. Never renumber an item: `Task 0.4` and `M3` are
  cited from this file and from the ADRs.

Seed the session task list from `docs/tasks/` when starting work, and treat the files as
the durable record — the session list is a working copy, not a second source of truth.

Content moves from the discussion log to the ADR by **rewriting**, never copying — the log
is private material, the ADR is the distilled public result. Accepted ADRs are immutable:
to change a decision, write a new ADR and mark the old one `Superseded by ADR-NNNN` (or
`Accepted — <aspect> amended by ADR-NNNN` if only part of it moved), leaving its body
alone.

Not every discussion produces an ADR; every discussion produces a log. Recording too
little is the failure mode — a short ADR beats none.

## What modelrack4j is

A plain-Java (no framework) library that turns layered HOCON config files into **named,
ready-to-use bundles of LangChain4j objects**, with file-watch hot reload. Define `SL`,
`SH`, `CR` in a config file; get a consistent `ChatModel` + `StreamingChatModel` +
`ModerationModel` + `ChatMemoryProvider` per name; edit the file and the registry picks it
up atomically, validated, without a restart.

It is an **unofficial, independent** library that depends on LangChain4j. Never use the
`langchain4j-` artifact prefix. Its first consumer is the owner's own application,
developed in parallel in a separate repository and named in `brainstorm/PLAN.md`.
Governing rule from the plan: *when library and application disagree, the application wins
and the library changes* — so a requirement traced to that application outranks a
preference of the library's own design.

## Build and test (once the Maven skeleton exists)

Maven multi-module, Apache 2.0, artifacts `io.github.<owner>:modelrack4j-*`. Local install
only for now (`-SNAPSHOT` to `~/.m2`); Maven Central publishing is deferred to a later
milestone.

```bash
mvn clean install                        # full build, all modules
mvn -pl modelrack4j-core -am test        # build core and its deps, run core tests
mvn -pl modelrack4j-core test -Dtest=LlmRegistryTest                        # single class
mvn -pl modelrack4j-core test -Dtest='LlmRegistryTest#reloadSwapsAtomically' # single method
mvn -Pintegration verify                 # provider tests against real APIs (keys from env)
```

Scope `-Dtest=` to a module with `-pl`. Running it from the root across all modules fails
in every module that does not contain the named test unless `-DfailIfNoTests=false` is
added.

Integration tests are skipped by default and require real API keys from the environment;
everything else must pass offline with no keys (that is what `FakeProviderFactory` in core
test scope is for).

Toolchain on this machine: JDK 21 (Temurin), Maven 3.8.7. The language floor is Java 17
(records are used throughout); confirm LangChain4j's own requirement and set
`maven.compiler.release` accordingly.

## Architecture: the load-bearing constraints

These are the parts that require reading several plan sections together. Do not
"simplify" them away — each protects a specific failure mode, and the full reasoning is in
`docs/adr/` (ADR-0002 … ADR-0014, backfilled from the plan's Appendix A). Read the ADR
before changing anything below; the summaries here are pointers, not the argument.

**Snapshot-wide atomicity (ADR-0012, widening ADR-0008).** All config
files are merged in memory into ONE snapshot. A reload parses → validates → builds every
changed bundle in a *staging area*, then swaps a single snapshot reference. Any failure
anywhere means nothing swaps, the previous snapshot stays live, and `onReloadFailure`
fires exactly once. Success fires exactly ONE `onReload(change)` with `updated`/`added`/
`removed` name sets. Per-bundle callbacks are derived from that object, never fired
independently — two callbacks would let an application observe new-SL with old-SH, which
is a correctness hazard for multi-model councils. The staging step is load-bearing:
builders throw for reasons `validate()` cannot predict.

**Resolve after merging, never per file (ADR-0007).** Typesafe Config separates parsing
from resolution. Parse each layer with `ConfigFactory.parseFile(...)` only, merge with
`withFallback` (lowest → highest precedence), then call `.resolve()` exactly ONCE on the
merged result. Resolving per file breaks mandatory `${VAR}` substitution in layered
setups. This needs a dedicated regression test.

**Core dependency isolation (ADR-0005).** `modelrack4j-core` depends ONLY on `langchain4j-core`
and `com.typesafe:config`. Each provider lives in its own module
(`modelrack4j-provider-openai|anthropic|gemini|glm`) implementing the `ProviderFactory`
SPI, discovered via `java.util.ServiceLoader` (`META-INF/services/...spi.ProviderFactory`).
Providers differ in *capabilities* — moderation is roughly OpenAI-family only, token
estimation varies — so `validate()` per factory is where capability checks fail fast.
`ChatMemoryProvider` is built in core (provider-independent), except the
`TokenCountEstimator` needed by token-window memory, which comes from the factory.

**Registry keys are config names, never provider names (ADR-0006).** Two named blocks may share
a provider and differ only in parameters. Change detection is per-name diff by *record
equality* on the parsed config, so `LlmConfig` must be an immutable record with validation
in its constructor/loader — invalid configs unrepresentable.

**Holder API is primary (ADR-0009).** `registry.get(name)` always returns the current bundle;
listeners are optional and secondary. The classic trap — callers caching a bundle at
startup and never seeing reloads — must be documented prominently in README and Javadoc.

**Watching directories, not files (ADR-0013).** `WatchService` registers on directories: watch
the deduplicated set of parent directories, filter by filename, treat ENTRY_CREATE and
ENTRY_MODIFY identically (editors write via temp-file-then-rename), debounce ~300 ms,
resolve symlinks to their real path and re-resolve after each event (Kubernetes ConfigMap
swaps the link target). macOS `WatchService` is polling-based internally — measure and
document the latency instead of claiming uniform real-time behaviour. Task 0.8 spike gates
M3.

**Lifecycle (ADR-0014).** A name removed from config is removed from the registry; `get()`
then throws `UnknownConfigurationException`. Superseded bundles are NOT closed in v1 —
in-flight requests may still hold them.

## Scope boundaries (say no to these)

- `AiServices`, `@Tool` methods, RAG retrievers, guardrails — code-shaped, not
  config-shaped. Permanently out of scope (ADR-0003).
- Provider pools / fallback / retry — Resilience4j territory, never.
- `EmbeddingModel` — not in v1 (does not depend on `ChatModel`).
- `ReloadableChatModel` hot-swap wrapper — designed for, deferred to v2. Note hot *reload*
  itself IS in v1 (M3); only the convenience wrapper is deferred (ADR-0011).
- Generic reloadable-config library positioning — Apache Commons Configuration owns that
  space (ADR-0002).

## Working practices for this repo

- **Phase 0 gates design details** — see `docs/tasks/phase-0-verification.md`. Verify those
  facts *against Maven Central and upstream sources*, never from recollection; that is the
  whole point of the phase. Several ADRs rest on assumptions those tasks confirm or refute,
  and each entry names the ADR its finding would amend.
- **`docs/tasks/open-decisions.md` needs the owner.** Ask; do not decide unilaterally.
- The §2 decision table in `brainstorm/PLAN.md` is closed: do not reopen those choices
  without asking. The ADRs carry the same decisions with their reasoning.
- Milestones run M0 → M5 in `docs/tasks/milestones.md` (v1 is done at M5). Note the open
  M3-vs-M4 ordering question recorded there.
- The `java-best-practices-modern` plugin skill is enabled for this project; use it for
  non-trivial Java.
