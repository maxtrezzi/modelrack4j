# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

**v1 is complete and the repository is public.** Seven Maven modules, four providers, hot
reload, a two-part manual and runnable examples. M0–M5 are done; M6 (GPG signing, Central
Portal publishing) is unscheduled by design, triggered by the library proving itself in the
owner's first real project rather than by a date. The version is `0.1.0-SNAPSHOT` with no
tag and no released artifact — public and released are separate, and only the first has
happened (ADR-0034).

**This file is tracked and public.** It drifted badly once — its Project state section still
claimed "no code exists yet" long after v1 shipped, and its watcher guidance still described
a symlink strategy ADR-0024 had reversed. That is why it is tracked rather than local: a
tracked file changes through review, a hidden one drifts unseen (ADR-0037). **Keep it
current in the same commit as the work it describes**, and treat a stale instruction here as
a defect, not as background noise — a future session will follow it.

Four documents matter, with different jobs:

- **`docs/tasks/`** — what to do next and whether it is done: the Phase 0 verification
  tasks, milestones M0–M6, the post-v1 items P1…, and any decision waiting on the owner.
  **Start here.**
- **`docs/adr/`** — why the work is shaped this way. ADR-0002 … ADR-0014 were backfilled
  from the plan's Appendix A; everything from ADR-0015 on was written as the decision was
  taken. Read the index in `docs/adr/README.md` for what governs what.
- **`docs/manual/`** — the user-facing account: a tutorial and a reference covering every
  configuration key, every public method, and what a reload guarantees.
- **`brainstorm/PLAN.md`** — the owner's original specification. Local-only, never
  committed. The tracked documents have since absorbed nearly all of it, so prefer them;
  consult the plan for intent the ADRs and the manual do not cover.

They overlap deliberately. Where they differ: the ADR wins on a *decision*, `docs/tasks/`
wins on *status*, the manual wins on *how a user drives it*, and the plan is the owner's
working copy rather than an authority.

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
  was *found*, not just that they finished. Never renumber an item: `Task 0.8` and `M3` are
  cited from this file, and others from the ADRs.

Seed the session task list from `docs/tasks/` when starting work, and treat the files as
the durable record — the session list is a working copy, not a second source of truth.

**Branch before starting.** Every task gets its own branch and nothing is committed
directly to `main` (ADR-0016). Name it after the work item — `task/0.1-pin-langchain4j-version`,
`milestone/m0-skeleton`, `decision/d2-repository-visibility`, or `docs/<slug>` for work with
no task ID. One branch carries the work, its status update in `docs/tasks/`, and any ADR it
produces.

Content moves from the discussion log to the ADR by **rewriting**, never copying — the log
is private material, the ADR is the distilled public result. Accepted ADRs are immutable:
to change a decision, write a new ADR and mark the old one `Superseded by ADR-NNNN` (or
`Accepted — <aspect> amended by ADR-NNNN` if only part of it moved), leaving its body
alone. **Frozen means frozen against additions too** — a later measurement, correction or
finding does not get appended to an accepted ADR, however well dated or however clearly it
confirms the decision. It goes to `docs/tasks/`, which is where "what was found" belongs
(ADR-0015). The only lines that may ever change in an accepted ADR are `Status`,
`Supersedes` and `Amends`, because those *are* the amend mechanism.

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

## Build and test

Maven multi-module, Apache 2.0, artifacts `io.github.maxtrezzi:modelrack4j-*` (ADR-0025).
Local install only for now (`-SNAPSHOT` to `~/.m2`); Central publishing is M6 and unscheduled.

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

Toolchain on this machine: JDK 25.0.3 (Temurin), Maven 3.8.7. The language floor is Java 17
and `maven.compiler.release` is set to it (ADR-0019); CI runs the floor, the development JDK
and the current LTS (ADR-0026).

**Model identifiers rot, and only a live run catches it.** Two of the four are now outside
upstream's enums, so `mvn -Pintegration verify` is the only check that a configured model
still exists. It cost two stale IDs the first time it ran (P6).

## Architecture: the load-bearing constraints

These are the parts that require reading several decisions together. Do not "simplify" them
away — each protects a specific failure mode, and the full reasoning is in `docs/adr/`. Read
the ADR before changing anything below; the summaries here are pointers, not the argument,
and where a summary and an ADR disagree the ADR wins and the summary is the bug.

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
setups. This has a dedicated regression test; keep it.

**Core dependency isolation (ADR-0005, amended by ADR-0020 and then ADR-0028).**
`modelrack4j-core` declares exactly four compile dependencies: `langchain4j-core`,
`com.typesafe:config`, `org.slf4j:slf4j-api` — for the watcher, whose thread has no caller
to throw at (ADR-0028) — and, for `ChatMemoryProvider` alone, which is *not* in
`langchain4j-core`, the `dev.langchain4j:langchain4j` aggregate with `opennlp-tools`
excluded. The aggregate costs one jar and adds no transitive dependency; the measurement is
in M0's verification block. **No provider artifact, ever.** Each provider lives in its own module
(`modelrack4j-provider-openai|anthropic|gemini|glm`) implementing the `ProviderFactory`
SPI, discovered via `java.util.ServiceLoader` (`META-INF/services/...spi.ProviderFactory`).
Providers differ in *capabilities* — moderation is OpenAI-only, and token estimation is
three-valued rather than a boolean: `ABSENT` (GLM), `LOCAL` (OpenAI), `REMOTE` (Anthropic,
Gemini), because a remote estimator puts a billed network call inside memory eviction
(ADR-0021, opt-in per ADR-0027). `validate()` per factory is where capability checks fail
fast.
`ChatMemoryProvider` is built in core (provider-independent), except the
`TokenCountEstimator` needed by token-window memory, which comes from the factory.

**Registry keys are config names, never provider names (ADR-0006).** Two named blocks may share
a provider and differ only in parameters. Change detection is per-name diff by *record
equality* on the parsed config, so `LlmConfig` must be an immutable record with validation
in its constructor/loader — invalid configs unrepresentable.

**Holder API is primary (ADR-0009).** `registry.get(name)` always returns the current bundle;
listeners are optional and secondary. The classic trap — callers caching a bundle at
startup and never seeing reloads — is documented prominently in the README and the Javadoc;
keep it there.

**Watching directories, not files (ADR-0013, symlink strategy reversed by ADR-0024).**
`WatchService` registers on directories: watch the deduplicated set of parent directories,
treat ENTRY_CREATE and ENTRY_MODIFY identically (editors write via temp-file-then-rename),
debounce ~300 ms, and re-register when a watched directory is lost.

**Register on the directory containing the configured path itself — the symlink, when it is
one — and NEVER on the resolved real path's directory.** Resolution still happens, to read
the file and to detect that it changed, but never to choose what to watch. The Task 0.8
spike refuted the opposite instruction, which this file carried for months: resolving to the
real path cannot see a Kubernetes ConfigMap swap the link target. The filename filter is
conditional on that — filter by filename for a plain file, but for a symlinked one accept
*any* event in the directory, then re-resolve and compare.

macOS `WatchService` is polling-based internally. Its latency is measured on Linux only and
the gap is stated rather than papered over; do not substitute a plausible figure.

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

- **Verify against upstream sources, never from recollection.** That was Phase 0's whole
  point and it outlived the phase: three of its eight verification tasks refuted the premise
  they were written with. Read the artifact with `javap` or `unzip`, query Central, fetch the
  upstream POM. "I believe the API is…" is how this project gets things wrong.
- **`docs/tasks/open-decisions.md` needs the owner.** Ask; do not decide unilaterally.
  D1–D3 are all settled, so the file is currently a record rather than a queue — a new
  entry there is a question for the owner, not work to pick up.
- The §2 decision table in `brainstorm/PLAN.md` is closed: do not reopen those choices
  without asking. The ADRs carry the same decisions with their reasoning.
- Milestones run M0 → M5 in `docs/tasks/milestones.md` and v1 was done at M5. **M6 is named
  there but has no entry of its own**, deliberately: it is unscheduled, so there is nothing
  to write down yet beyond its trigger. Post-v1 work is P1… in `docs/tasks/post-v1.md`.
- **The repository is public (ADR-0034).** Secret discipline is pre-push, not pre-release,
  and `brainstorm/` is a confidentiality boundary rather than a convention. Checks that read
  the working tree answer "does it work here", not "does it work for someone cloning" — the
  two came apart the day it went public.
- **Do not grow the `NOTICE` file.** Apache 2.0 §4(d) binds every downstream redistributor
  to carry its contents forever, so it is four lines on purpose (ADR-0035). It ships inside
  every jar via a `<resources>` entry in the parent POM — and that entry *replaces* the
  default `src/main/resources` rather than adding to it, so the default is restated there.
  Deleting it as redundant silently empties every provider's `META-INF/services` file and
  breaks `ServiceLoader` with no compile error.
- The `java-best-practices-modern` plugin skill is enabled for this project; use it for
  non-trivial Java.
