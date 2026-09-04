# AGENTS.md

Guidance for a coding agent working in this repository. It was called `CLAUDE.md` until
2026-09-02, when it was renamed so that it names the job rather than one tool
([ADR-0046](docs/adr/0046-agent-guidance-lives-in-agents-md.md)). `CLAUDE.md` is still there
and points here; every ADR that cites the old name still resolves.

## Project state

**v1 is complete, the repository is public, and `0.1.0` is released.** Seven Maven modules,
four providers, hot reload, a two-part manual and runnable examples. M0–M6 are done: M6's
trigger fired on 2026-09-02, when the owner tested the library and judged it publishable, and
`io.github.maxtrezzi:modelrack4j-*:0.1.0` was signed and published to Maven Central the same
evening. `modelrack4j-examples` is not published and is confirmed absent from Central.

**A published version can never be changed or deleted.** That is new, and it changes what a
mistake costs: before M6 a wrong API shape was a commit, now it is a permanent artifact. The
project is `0.x` and its CHANGELOG reserves the right to break in a minor, so a fix is
allowed — it is just also permanent. Releasing is ADR-0045: everything that signs or uploads
lives in the `release` profile, `autoPublish=false` keeps a human at the Portal, and the tag
comes after the publish.

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

**`main` is protected on the remote (ADR-0040), but not against you.** A pull request is
required, force-pushing and deleting are blocked, and the five checks in
`.github/workflows/build.yml` must pass and be current with the branch before a merge, with
no approving review needed — so for an outside contributor a green build is the whole gate.
**`enforce_admins` is `false`, and the only collaborator is an admin**, which is every
session working in this repository: none of those gates will actually stop you. Treat the
branch rule as binding anyway. It is a rule you keep because it is right, not because
something enforces it, and ADR-0040 accepts that trade deliberately — turning
`enforce_admins` on is all-or-nothing on GitHub's side and would also impose a fake
single-maintainer review requirement.

One branch may carry several `docs/tasks/` entries when they are genuinely one piece of
work — see the convention note in `docs/tasks/README.md`, and say so in the entries, so a
branch matching no identifier reads as a decision rather than as drift.

**ADR numbers are only safe once they are on `main`.** Two branches that each take "the next
free number" are both correct and still collide, and `build/check-docs.py` cannot warn about
it because from inside either branch nothing is wrong. Renumbering is cheap while nothing is
pushed and the ADR is referenced from nowhere outside the repository, and stops being cheap
after either. This has already happened once (P13). `CONTRIBUTING.md` carries the
contributor-facing version of this rule, because an outside contributor never reads this
file.

Content moves from the discussion log to the ADR by **rewriting**, never copying — the log
is private material, the ADR is the distilled public result. Accepted ADRs are immutable **in
their substance**: to change a decision, write a new ADR and mark the old one
`Superseded by ADR-NNNN` (or `Accepted — <aspect> amended by ADR-NNNN` if only part of it
moved), leaving the argument alone. **A later measurement, correction or finding does not get
appended**, however well dated or however clearly it confirms the decision. It goes to
`docs/tasks/`, which is where "what was found" belongs (ADR-0015). `Status`, `Supersedes` and
`Amends` change freely, because those *are* the amend mechanism.

**One narrow thing may be completed in place: the conditions of a figure the ADR already
quotes.** The owner ruled this on 2026-09-01, after ADR-0038's "about two per million" was
found to name no machine. A number whose meaning depends on the hardware is incomplete
without it, and supplying it adds nothing to the argument, contradicts nothing, and changes
no decision — that is what makes it a detail rather than substance. The exception stops
exactly there. It is not a licence to append a finding, a date, a correction, or a "this
later turned out to be…"; if the new words change what the ADR *claims*, it is a new ADR.

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
Publishing lives entirely in the `release` profile (ADR-0045), so an ordinary build never
signs and never contacts Central. A release needs Maven 3.9.2+ — `/usr/bin/mvn` here is 3.8.7
and SDKMAN's 3.9.16 is only on an *interactive* shell's `PATH` — and it needs the GPG
passphrase, which is not cached. The passphrase does not require an interactive shell: gpg's
default pinentry here is `pinentry-gnome3` and `DISPLAY` is set, so a signing step started
from any shell opens a dialog on the desktop for a human to type into. **Do not conclude
otherwise from `gpg --pinentry-mode error`** — that flag orders gpg to fail rather than ask,
so its `No pinentry` says nothing about what is available.
`build/check-release-bundle.sh` checks a built bundle before it is uploaded.

```bash
mvn clean install                        # full build, all modules
mvn -pl modelrack4j-core -am test        # build core and its deps, run core tests
mvn -pl modelrack4j-core test -Dtest=LlmRegistryTest                        # single class
mvn -pl modelrack4j-core test -Dtest='LlmRegistryTest#reloadSwapsAtomically' # single method
mvn -Pintegration verify                 # provider tests against real APIs (keys from env)
mvn -pl modelrack4j-core org.pitest:pitest-maven:mutationCoverage   # mutation testing, core only
./run-atomic.sh                          # run an example (also swap, chat, council; --help each)
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

**Mutation testing is configured on core and nowhere else, and that is a money rule
(ADR-0041).** PIT runs the covering tests once per mutant; each provider module carries an
`*IT.java` that calls a paid API under `-Pintegration`. Never add the plugin to a provider
module, to `modelrack4j-examples`, or to the parent POM — the parent inherits it into every
module, and because the goal is bound to no lifecycle phase this stays invisible until someone
runs `mutationCoverage` from the reactor root, at which point the bill is the error message.
`mvn clean install` is unaffected. **It is also never in CI, in any form (ADR-0043)** — not a
required check, not an optional job, not a nightly: a full run is 122 s against a whole gate of
about 42 s, and more to the point a job at `mutationThreshold = 0` is permanently green while
one equivalent mutant makes any threshold either permanently red or arbitrary. Run it by hand,
on a tree that has stopped moving. `mutationThreshold` is `0` on purpose: the deliverable is
the survivor list in `target/pit-reports/`, not the score, and a survivor is a question about
the tests rather than a defect. P17 is the worked example — four defects in the suite, none in
the code, and the most useful one was a test whose *name* described a contract its assertion
never checked. **Read `NO_COVERAGE` and `SURVIVED` as different questions, and answer both by
running something.** P27's single survivor was `Optional.empty()` mutated to
`Optional.empty()` — an equivalent mutant, no gap, nothing to do — while its uncovered line
was the opposite: `WritableFileConfigSource.destination()` guarded a catch with `Files.exists`,
which follows links, so both causes the comment named were filtered out before the `try`. A
throwaway probe established that; the fix was `NOFOLLOW_LINKS`, a corrected comment **and** a
test, and that test is what covers the branch now. Neither answer was visible from the report
alone. **The `NO_COVERAGE` you will see today is a different line** — the cleanup branch in
`stage()`, which needs a filesystem that fails between `createTempFile` and `writeString` and
is left untested on purpose. Take the current report from `target/pit-reports/`, not from this
paragraph: what is uncovered moves as the code does, and P29 found this description already
pointing at the wrong method. `docs/tasks/post-v1.md` carries the per-run tables. Mutants are
deterministic syntactic edits, so none of this bears on the concurrency guarantee in ADR-0038;
do not read a high score as evidence for it.

**Model identifiers rot, and only a live run catches it.** Two of the four are now outside
upstream's enums, so `mvn -Pintegration verify` is the only check that a configured model
still exists. It cost two stale IDs the first time it ran (P6).

## Architecture: the load-bearing constraints

These are the parts that require reading several decisions together. Do not "simplify" them
away — each protects a specific failure mode, and the full reasoning is in `docs/adr/`. Read
the ADR before changing anything below; the summaries here are pointers, not the argument,
and where a summary and an ADR disagree the ADR wins and the summary is the bug.

**Snapshot-wide atomicity (ADR-0012, widening ADR-0008, amended by ADR-0038).** All config
files are merged in memory into ONE snapshot. A reload parses → validates → builds every
changed bundle in a *staging area*, then swaps a single snapshot reference. Any failure
anywhere means nothing swaps, the previous snapshot stays live, and `onReloadFailure`
fires exactly once. Success fires exactly ONE `onReload(change)` with `updated`/`added`/
`removed` name sets. Per-bundle callbacks are derived from that object, never fired
independently — two callbacks would let an application observe new-SL with old-SH, which
is a correctness hazard for multi-model councils. The staging step is load-bearing:
builders throw for reasons `validate()` cannot predict.

**How much of that atomicity a caller gets is a separate question, and ADR-0038 answers it.**
`get()` reads the live snapshot on every call, so two consecutive calls can straddle a swap
and return bundles from different generations — measured at about two per million pairs, on
an AMD Ryzen 7 7840HS running Temurin 25.
`snapshot()` reads the generation once and hands back an `LlmSnapshot`, and `get()` now
delegates to it. Do not re-describe `get()` as "a volatile read and a map lookup": it also
allocates that wrapper (P14). A held snapshot never updates, so one per unit of work, never
one at startup — that is the caching trap again.

**Resolve after merging, never per layer (ADR-0007).** Typesafe Config separates parsing
from resolution. Parse each layer **without resolving**, merge with `withFallback`
(lowest → highest precedence), then call `.resolve()` exactly ONCE on the merged result.
Resolving per layer breaks mandatory `${VAR}` substitution in layered setups. This has a
dedicated regression test; keep it.

**A layer is a `ConfigSource`, not a file (ADR-0042).** `ConfigSource` is `id()` plus
`text()` and names no file, path or URL, so a layer can be a database row; `ChangeNotifier`
carries "how do I learn this changed" separately, and `FileChangeNotifier` wraps the existing
watcher unchanged. Three consequences that look like tidying and are not:

- **A file layer is parsed with `parseFile`, everything else with `parseString`, and the
  `instanceof` in `ConfigLoader.parse` is load-bearing.** `include "sibling.conf"` resolves
  relative to the file containing it, and only `parseFile` knows which file that is. Give the
  same bytes to `parseString` and the includer falls back to the classpath — and because an
  include is allow-missing by default, the included block then **disappears with no error**.
  P19 shipped that regression and a review caught it; there is now a regression test.
- **`LlmRegistry.reload()` is public, and the `synchronized (reloadLock)` around the
  compare-then-swap is not removable.** The watcher thread is no longer the only writer: an
  application can reload too, and two reloads at once would both read the same snapshot and
  the later would discard the earlier in silence, after its listeners had announced it.
  Readers do not take that lock, so ADR-0038 is unaffected.
- **`watch(true)` asks the layers, never the builder method that supplied them, and
  `configFiles(...)` keeps no path list of its own (ADR-0050).** It watches every `FileBacked`
  layer and ignores the rest; only a registry where *no* layer is a file is refused. The old
  condition read a `watchableFiles` field that only `configFiles(...)` filled, so a registry
  whose every layer was a file was refused for having none — which also blocked `store()` and
  hot reload together, because a `WritableConfigSource` can only arrive through `sources(...)`.
  ADR-0042's own Decision already said "without file sources", so the code had been stricter
  than the ADR it came from. Do not reintroduce a field that remembers which method was called.

**A layer the application owns can be written back (P20).** `store(target, text)` stages the
text, validates the whole configuration against the staged copy, publishes, and only then
stores — and puts the previous snapshot back if storing fails. Four things about it are
load-bearing:

- **The order is the contract, and it is why the method exists at all.** Writing first and
  reloading second — the only route P19 left — leaves invalid text in the layer when the
  reload rejects it, and the next start fails. Publishing *before* storing is what leaves a
  waking watcher an empty diff, so a store fires no listener with no flag to arrange it.
- **`storeIfUnchanged(target, expected, text)` is a third writer, and the reload lock now
  covers a read-modify-write.** A plain `store` cannot hold the caller's `text()` and its own
  write together; the compare happens inside the lock, against a fresh `text()`, and throws
  `StaleLayerException` carrying the current text. Two concurrent stores are still serialised
  either way — what the CAS adds is that the *caller's* read is part of the deal.
- **The registry stores text, never an `LlmConfig`.** An application holds resolved values,
  so an API that took one back would write the resolved secret into the layer. Measured in
  P20: `api-key = ${?HOME}` in the layer, `/home/...` from `config.apiKey()`.
- **ADR-0042's include hazard has a write-side twin, and the check compares directories
  rather than testing for a symlink.** A staged file sits beside its destination so an
  include resolves during validation the way it will resolve afterwards — but the layer is
  parsed through the *configured* path, so when a link points into another directory the two
  disagree. Measured with `config-1.4.9`: parsing through the link finds the sibling next to
  the link, parsing the staged copy finds the one next to the target. That combination is
  refused, and only on the validating path: a plain `WritableConfigSource.write` validates
  nothing and must not inherit the refusal. Resolve the destination **once**, in `stage()`,
  and carry it to the commit — resolving again lets a ConfigMap swap the link in between.

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
(ADR-0021, opt-in per ADR-0027). **A factory *reports* a capability; core enforces it
(ADR-0048).** `tokenEstimation()` and `supportsModeration()` say what the provider can do,
and `SnapshotLoader.validateCapabilities` owns both the rule and the message, so every
provider refuses the same configuration in the same words. Do not restate either check in a
provider's `validate()` — that is now only for a rule core cannot see. P27 emptied the three
bodies that had been doing it; P25 then gave one of them real work, so **three of the four are
empty and GLM's is not** (ADR-0049). Its rule is the boundary to copy from rather than the
code: a provider may check the *shape* of a credential when its own code requires that shape
before it makes any call — GLM parses the key and signs a token with it, so `id.secret` with a
secret of at least 16 bytes is a property of code on the classpath. It may never check a
credential's content, or a catalogue of values a vendor controls, which is why `model-name`
is still unvalidated. `supportsModeration()` defaults
to `true` for compatibility rather than for truth, which is why `requireProduced` still
catches a missing model afterwards: that fallback is what makes the permissive default safe,
and it is not redundant.
`ChatMemoryProvider` is built in core (provider-independent), except the
`TokenCountEstimator` needed by token-window memory, which comes from the factory.

**Registry keys are config names, never provider names (ADR-0006).** Two named blocks may share
a provider and differ only in parameters. Change detection is per-name diff by *record
equality* on the parsed config, so `LlmConfig` must be an immutable record with validation
in its constructor/loader — invalid configs unrepresentable.

**`LlmConfig.toString()` is overridden to hide `apiKey`, and `equals` is deliberately not
(ADR-0047).** The component holds the credential *after* substitution, so the generated
`toString()` a record gives you puts a real key into any log line that prints a config or a
bundle — and `LlmBundle` carries the config, so it leaks the same way. Only `toString()` is
redacted: moving the redaction into `equals` would make a rotated key a configuration change
that never reloads, which is why one test asserts both halves at once. The same reasoning
already guards `ConfigSource.id()`, which the library itself prints.

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

**A latency figure carries the machine it came from, or it is not quoted at all.** "Measured
on Linux, 0.50 ms median" tells a reader nothing they can reproduce or compare against: a
number that depends on the hardware is meaningless without it. The three places that quote
the watcher figures now name the machine — AMD Ryzen 7 7840HS, ext4 on NVMe, Pop!_OS 24.04
(kernel 7.0.11), Temurin 25 — and say in the same breath that one machine is not a benchmark.
If you measure something new on this machine, record the machine with it, in `docs/tasks/`.

**Lifecycle (ADR-0014).** A name removed from config is removed from the registry; `get()`
then throws `UnknownConfigurationException`. Superseded bundles are NOT closed in v1 —
in-flight requests may still hold them.

## Scope boundaries (say no to these)

- **Configuring** `AiServices`, `@Tool` methods, RAG retrievers or guardrails from a file —
  code-shaped, not config-shaped. Permanently out of scope (ADR-0003). ***Using* them is not
  out of scope and never was**: they are registered on an `AiServices`, and a bundle holds the
  objects an `AiServices` is built from. The
  documentation said this badly enough to read as a limit on the reader's own application,
  which is what P21 fixed — README and the reference now carry a *What you still write
  yourself* section, and `ConsoleChat`'s `/tools` command runs an `AiServices` proxy with a
  `@Tool` method. Do not let that wording drift back toward "not supported".
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
- **That rule covers the numbers you use to describe your own work, and this is where it
  gets broken.** Counts, word deltas, "N places", "the largest", "N precedents" — run the
  command that produces the figure *before* writing the sentence, not after, and prefer the
  measurement to an adjective so the next reader can re-run it. P11 is the cautionary tale.
  Do not read it as "the counts of what to fix were fine and only the write-up drifted" — an
  early draft of that entry claimed exactly this, and an independent review refuted it from
  the entry's own table. One figure went through three wrong versions in a row, each written
  while correcting the previous one, and seven further counts were wrong once each. A
  superlative is a claim; if you have not measured it, do not write it. Note that
  `build/check-docs.py` cannot catch any of this — it checks links, ADR status lines and
  numbering, not whether a sentence is true. The check that did catch it was a session with
  no memory of writing the text, which is the only kind that works here. P14 adds the cheaper
  half of the same rule: three of its findings were enumerations that contradicted their own
  leading number ("seven modules: parent, core, four providers, BOM, examples" is eight), so
  count the list you just wrote before you name its size. And a completeness audit — "every
  public member, 28 of 28" — is true only on the day it runs, so date it or it becomes a
  false claim the next time the API grows.
- **Grepping the figure is not enough; read the whole diff.** The two errors the third review
  of P11 found were invisible to a grep for the number, because the write-up was checked
  against one commit and the work spanned several. One of them was not a miscount at all: the
  entry listed a metaphor it had *written* among the metaphors it had *removed*, and no count
  of anything would have caught it. Before you describe a change, run
  `git diff main..HEAD` over it and read the output to the end — including when it is long,
  which is exactly when the previous session stopped. **P15 shows the rule reaches a write-up's
  account of its own fix.** P14 wrote that it had put a dated marker on all three of its
  miscounts and had put one on two of them, and named a missing date as the fix for a stale
  completeness audit without adding that date. Neither is a miscount, and both are visible
  only by reading P14's diff against P14's sentences. **P19 adds the next rung: reading the
  diff is not reading the file.** Its `056360d` added a `database)` case at line 28 of
  `build/run-example.sh`, and the two lines that change made stale — the launcher telling
  anyone without a key that `./run-atomic.sh` is *the* free example — sat at lines 76 and
  139, while that diff's last hunk ended at line 55. Three passes read the diff and none saw
  them, because a diff never shows them. When your change touches a file, read that file end
  to end: the lines describing the thing you just changed are exactly the ones a diff hides.
- **An over-claim an ADR corrects can survive in wording the ADR never uses.** ADR-0038's was
  made in four places in four different forms, and took three passes to clear. P14 found two —
  one repeating the sentence ADR-0038 quotes, one a near-paraphrase of it in the example
  ADR-0038 is about — and reported the count as two. The third restated the claim in a
  reader's own terms, in the README's opening pitch, where it had sat since M5 through one
  rewrite of its own paragraph (P15). The fourth was in `LlmRegistry`'s own class Javadoc,
  fifty-two lines above the `get()` Javadoc that contradicts it, and outlasted both passes
  because both scoped themselves to *documentation* and core's `src/main` is not filed under
  that heading (P16). Re-read the passages that *make* a claim, not only the ones that repeat
  its wording, and **read the class the ADR is about, not only the prose about it** — P16's
  own grep over `*.md`, `*.java` and `*.conf` missed the fourth copy, because four wordings of
  one claim share no phrase to grep for. It surfaced only from reading `LlmRegistry` end to
  end for an unrelated check.
- **Prose that was true when it was written is what later code falsifies, and nothing
  re-reads it.** Every false statement P19 found in the manual had been correct on the day it
  was committed. `docs/manual/part-2-reference.md` said `close()` "is safe to call from a
  listener — that case is detected rather than deadlocking", written in P3 (`65b8a4b`) and
  true then: the watcher thread was the only reloader and no reload lock existed. P19 made
  `reload()` public, and that sentence became a deadlock: a probe's `reload()` had still not
  returned when the probe gave up on it after fifteen seconds, and only an interrupt released
  it. The reference said `ConsoleChat` needs "one provider key", written in P4 (`d487fed`)
  and falsified by P18 (`1be72e4`), which created `examples.conf` with two providers and made
  it the default — it stayed false through a whole item before anyone looked. So when you
  change what the code *does*, go and find the sentences that describe the old behaviour. Do
  not grep
  for the words your new code uses: the stale sentence and the new mechanism share no
  vocabulary, which is why every one of these was found by reading rather than by searching.
  A completeness audit cannot help here either — enumerating the API finds a member no
  document mentions, and is structurally blind to a member some document describes wrongly.
- **And some of it was never true: a wrapper makes claims about code it does not contain.**
  P18 built `build/run-example.sh` over five Java mains and gave `run-chat.sh` and
  `run-council.sh` one shared `takes_config` flag, so a single branch printed ConsoleChat's
  wording for both: "Pass several files to see layering". `ThreeModelCouncil` has rejected
  anything but one argument since M2 (`d382c16`), and that `args.length != 1` was sitting in
  the file at `1be72e4`, the commit that promised the opposite. The sentence was false the day
  it was written, contradicted by code nobody opened. The other P18 defect in the same file
  needed a different check again: a configuration path was verified against two directories
  and then handed verbatim to a Maven run that looks in one, which no amount of reading
  reveals and the first run from another directory does. So when you write something that
  describes code elsewhere — a script's help, a README command, a manual's account of an
  example — open that code, and run the thing in the configuration your text claims to
  support. **P26 is the same rule one step further in: paste the output that command
  produced, not output from a neighbouring one.** Step 7 of the tutorial showed the library's
  `WARN` for a rejected reload above the `mvn -q … exec:java` command that suppresses it —
  genuine output of some run, but not of the run the page prints. Nothing about the text
  looked invented, which is why every documentation pass since P3 read past it; only running
  the printed command and comparing the terminal to the page found it.
- **User-facing prose has a register, and it is not this file's (ADR-0039).** The README,
  `docs/manual/`, public Javadoc, the commented `.conf` examples, `CONTRIBUTING.md` and the
  CHANGELOG are written for a technical reader at roughly B2 English who does not read it as
  a first language. Two tests per sentence: would a reader who does not yet know the
  mechanism parse it, and would a non-native reader parse it without a dictionary? Brevity
  stays the default — what the rule constrains is compressing meaning into metaphor or idiom,
  not length. **`docs/adr/`, `docs/tasks/` and this file are deliberately exempt**: their
  readers already hold the context, and an accepted ADR's body cannot be edited anyway. Do
  not "fix" their register, and do not let a user-facing paragraph drift back toward it.
- **`docs/tasks/open-decisions.md` needs the owner.** Ask; do not decide unilaterally. A new
  entry there is a question for the owner, not work to pick up, and an entry marked
  `Needs decision` blocks the code that depends on it rather than inviting a guess. D1–D4 are
  settled; **D5 and D6 are open** and have been since `5ae070a`. Read the file for the current
  list rather than trusting this sentence — it said "all settled" for a day after two entries
  had been added (P29).
- The §2 decision table in `brainstorm/PLAN.md` is closed: do not reopen those choices
  without asking. The ADRs carry the same decisions with their reasoning.
- Milestones run M0 → M6 in `docs/tasks/milestones.md`; v1 was done at M5, and M6 gained its
  own entry on 2026-09-02 when its trigger fired. Post-v1 work is P1… in
  `docs/tasks/post-v1.md`.
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
  non-trivial Java. It is version-aware and loads exactly one profile: **this project is
  Java 17**, so the profile is `java-17` and everything from 21 on — pattern matching for
  `switch`, record patterns, virtual threads, `ExecutorService` in try-with-resources — is
  unavailable however new the local JDK is. `CONTRIBUTING.md` carries the human-readable half
  of the same rule, stated as an idiom rather than as a tool, because an outside contributor
  has no access to the skill.
