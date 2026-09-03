# Work items

What to do next, and what is done. The reasoning behind *why* the work is shaped this way
lives in [`../adr/`](../adr/README.md) — tasks and ADRs answer different questions and
should not restate each other.

| Question | Answer lives in |
|---|---|
| What do I do next? Is it done? | here |
| Why is it built this way? What did we reject? | [`../adr/`](../adr/README.md) |

A task that settles a design question closes by writing an ADR and linking to it. A task
that merely gets work done closes by being marked `Done`.

## Files

- [`phase-0-verification.md`](phase-0-verification.md) — Tasks 0.1–0.8. Facts that must be
  checked against upstream sources before design details are fixed. Several ADRs rest on
  assumptions these tasks confirm or refute.
- [`milestones.md`](milestones.md) — M0–M6. What ships, in what order.
- [`open-decisions.md`](open-decisions.md) — items blocked on the owner, not on work.
- [`post-v1.md`](post-v1.md) — P1, P2, … work taken on after v1 closed.

## Conventions

**Identifiers never change.** `Task 0.4` and `M3` are cited from `AGENTS.md` and from the
ADRs. Renumbering silently breaks those references, so items are retired in place rather
than renumbered, and new work takes the next free number.

**Status values**

| Status | Meaning |
|---|---|
| `Not started` | Ready to pick up |
| `In progress` | Someone is on it |
| `Blocked` | Waiting on another task; names which one |
| `Needs decision` | Waiting on the owner, not on work |
| `Done` | Finished, with its outcome recorded in the entry |

**Closing a task** means recording what was *found*, not just ticking a box — a
verification task whose answer is lost has to be redone. Findings that contradict a current
ADR trigger an amendment to that ADR (see the folder README there for the mechanism).

**Every task gets its own branch** ([ADR-0016](../adr/0016-one-feature-branch-per-task.md)).
Branch before starting, never commit to `main`, and name the branch after the item:
`task/0.1-pin-langchain4j-version`, `milestone/m0-skeleton`,
`decision/d2-repository-visibility`. The branch carries the work, the status update here,
and any ADR the task produces.

**One branch may carry several entries when they are one piece of work.** P11 through P15
share `docs/documentation-reorganisation` because they are one documentation
reorganisation split into five records, not five tasks batched for convenience — and
because two of them independently took the same next-free ADR number, which only a shared
branch could reconcile. P14 joined them because most of what it corrects was written by the
other three, and P15 because most of what it corrects was written by P14. Name such a branch
after the work rather than after any one item, and say so in the entries, so that a branch
matching no identifier reads as a decision rather than as drift.

## Status board

Phase 0 gates everything else; nothing below M0 should start before its blockers clear.

| Item | What | Status |
|---|---|---|
| [Task 0.1](phase-0-verification.md#task-01--pin-the-langchain4j-version) | Pin the LangChain4j version | **Done** — `1.19.0` |
| [Task 0.2](phase-0-verification.md#task-02--verify-the-java-baseline) | Verify the Java baseline | **Done** — `release` 17 |
| [Task 0.3](phase-0-verification.md#task-03--glm-module-status) | GLM module status | **Done** — module is maintained |
| [Task 0.4](phase-0-verification.md#task-04--which-gemini-module) | Which Gemini module | **Done** — the stable one |
| [Task 0.5](phase-0-verification.md#task-05--confirm-interface-names) | Confirm interface names | **Done** — one mismatch |
| [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix) | Provider capability matrix | **Done** — expectation refuted |
| [Task 0.7](phase-0-verification.md#task-07--name-and-coordinates) | Name and coordinates | **Done** — `io.github.maxtrezzi` |
| [Task 0.8](phase-0-verification.md#task-08--watch-strategy-spike) | Watch strategy spike | **Partly done** — macOS unmeasured |
| [M0](milestones.md#m0--skeleton-and-ci) | Skeleton and CI | **Done** — build green |
| [M1](milestones.md#m1--core-without-watching) | Core without watching | **Done** — 37 tests green |
| [M2](milestones.md#m2--openai-and-anthropic) | OpenAI and Anthropic | **Done** — 52 tests green |
| [M3](milestones.md#m3--hot-reload) | Hot reload | **Done** — 69 tests green |
| [M4](milestones.md#m4--gemini-and-glm) | Gemini and GLM | **Done** — 87 tests green |
| [M5](milestones.md#m5--release-readiness) | Release readiness — **v1 done** | **Done** — README, CHANGELOG, artifacts verified |
| [M6](milestones.md#m6--gpg-signing-and-central-portal-publishing) | GPG signing and Central Portal publishing | **Done** — `0.1.0` on Maven Central, examples excluded, resolved from an empty repository |
| [P1](post-v1.md#p1--console-chat-example) | Console chat example | **Done** — reload seen live |
| [P2](post-v1.md#p2--a-short-description-per-configuration) | A short description per configuration | **Done** — ADR-0032 |
| [P3](post-v1.md#p3--the-manual) | The manual: tutorial and reference | **Done** — every command run |
| [P4](post-v1.md#p4--two-examples-for-the-two-undemonstrated-strengths) | Examples for the two undemonstrated strengths | **Done** — atomicity shown, and made to fail |
| [P5](post-v1.md#p5--repository-hygiene-ignore-rules-and-a-contributing-guide) | Ignore rules and a contributing guide | **Done** — rules tested, one gap found |
| [P6](post-v1.md#p6--the-integration-tests-against-live-apis) | The integration tests against live APIs | **Done** — all four answered; ADR-0033 |
| [P7](post-v1.md#p7--closing-out-the-outside-review-of-the-public-repository) | Closing out the outside review | **Done** — ADR-0035, ADR-0037 |
| [P8](post-v1.md#p8--status-line-drift-and-a-check-that-would-have-caught-it) | Status-line drift, and a check for it | **Done** — 4 ADRs fixed; `build/check-docs.py` |
| [P9](post-v1.md#p9--the-three-things-that-had-to-be-right-before-a-first-release) | Pre-release correctness and metadata | **Done** — `snapshot()`, ADR-0038 |
| [P10](post-v1.md#p10--a-code-review-of-llmsnapshot-and-a-self-correction-found-while-checking-the-fix) | Review of `LlmSnapshot`, plus a self-caught perf regression | **Done** — delegation, no duplication |
| [P11](post-v1.md#p11--the-user-facing-text-read-as-a-non-native-reader-would-read-it) | The user-facing text, read as a non-native reader would | **Done** — 2 stale claims, 23 phrases |
| [P12](post-v1.md#p12--testing-the-examples-by-hand-and-a-live-break-in-anthropics-sampling-parameters) | Testing the examples by hand | **Done** — Anthropic dropped non-default `temperature` on claude-sonnet-5 |
| [P13](post-v1.md#p13--branch-protection-on-main) | Branch protection on `main` | **Done** — required checks, PR required, 0 reviews; ADR-0040 |
| [P14](post-v1.md#p14--a-coherence-pass-over-the-tracked-documentation) | A coherence pass over the tracked documentation | **Done** — 8 substantive findings, 3 of them miscounts |
| [P15](post-v1.md#p15--a-second-coherence-pass-and-what-the-first-one-missed) | A second coherence pass over the same documentation | **Done** — 5 defects, 2 of them in P14's own write-up |
| [P16](post-v1.md#p16--a-third-coherence-pass-and-the-surface-the-first-two-searched-past) | A third coherence pass, over the code's own Javadoc too | **Done** — 2 defects; a 4th copy of the ADR-0038 over-claim |
| [P17](post-v1.md#p17--mutation-testing-on-core) | Mutation testing on core | **Done** — 4 defects in the suite, none in the code; ADR-0041 |
| [P18](post-v1.md#p18--the-distance-between-arriving-and-running-something) | README ordering, a launcher for the examples, and where the Java idiom rule lives | **Done** — config example moved from line 165 to line 11; one `run-*.sh` per example; `examples.conf` |
| [P19](post-v1.md#p19--configuration-sources-and-a-reload-the-application-can-ask-for) | Configuration sources, and a reload the application can ask for | **Done** — a layer can be a database row; ADR-0042 |
| [P20](post-v1.md#p20--writing-a-configuration-layer-back) | Writing a configuration layer back | **Done** — validate before storing; the edit API built and removed; ADR-0044 |
| [P21](post-v1.md#p21--what-the-library-leaves-available-said-out-loud) | What the library leaves available, said out loud | **Done** — *configuring* those from a file is what is out of scope; `ConsoleChat` gained `/tools`, and the example set stayed at five |
| [P22](post-v1.md#p22--the-council-asks-your-question-and-mavens-warning-stops-landing-in-the-output) | `ThreeModelCouncil` asks the questions you type, and the launcher silences a warning that is not ours | **Done** — a question loop on standard input with no default and `/exit` to leave; `--sun-misc-unsafe-memory-access=allow`, probed rather than assumed |
| [P23](post-v1.md#p23--housekeeping-key-shaped-files-cannot-be-committed-and-the-guidance-is-called-agentsmd) | Housekeeping: key-shaped files cannot be committed, and the guidance is called `AGENTS.md` | **Done** — 12 ignore patterns, tested in both directions; ADR-0046 |
| [P24](post-v1.md#p24--watchtrue-cannot-see-file-backed-layers-given-through-sources) | `watch(true)` cannot see file-backed layers given through `sources(...)` | **Not started** — silent drift between two path lists |
| [P25](post-v1.md#p25--a-malformed-glm-key-fails-before-the-call-past-the-exception-guarantee) | A malformed GLM key fails before the call, past the exception guarantee | **Done** — three pre-call failures, not one; the shape is validated at load; ADR-0049 |
| [P26](post-v1.md#p26--two-documentation-gaps) | Two documentation gaps | **Done** — both reproduced; the tutorial's step 7 printed a `WARN` its own `mvn -q` command suppresses |
| [P27](post-v1.md#p27--a-review-of-all-seven-modules-the-examples-and-the-manual) | A review of all seven modules, the examples and the manual | **Done** — 8 findings, none critical; a credential in `LlmConfig.toString()`; ADR-0047 and ADR-0048 |
| [P28](post-v1.md#p28--a-failing-model-ends-the-council-round) | A failing model ends the council round | **Done** — one dead key cost all three answers; the other two examples already caught it |
| [D1](open-decisions.md#d1--glm-route-if-no-maintained-module-exists) | GLM route if no maintained module | **Closed** — never became live |
| [D2](open-decisions.md#d2--repository-visibility) | Repository visibility | **Settled** — public, not released; ADR-0034 |
| [D3](open-decisions.md#d3--token-window-memory-on-a-remote-estimator) | Token-window memory on a remote estimator | **Settled** — opt-in flag |
| [D4](open-decisions.md#d4--mutation-testing-in-ci) | Mutation testing in CI | **Settled** — never, in any form; ADR-0043 |
| [D5](open-decisions.md#d5--a-version-token-for-optimistic-concurrency) | A version token for optimistic concurrency | **Needs decision** |
| [D6](open-decisions.md#d6--cannot-store-is-not-your-configuration-is-invalid) | "Cannot store" is not "your configuration is invalid" | **Needs decision** |

**Phase 0 is complete except for one measurement, and M0 is done — the build is green.** Tasks 0.1–0.7 are
done; Task 0.8 is done on Linux and open only on the macOS latency figure, which qualifies
documentation rather than changing code and so gates neither M0 nor M3.

What the phase settled: the pin at `1.19.0` and the two-BOM import it later needed, `release`
17 as the compile target, the type names and the one mismatch ADR-0020 resolves, the
capability matrix, GLM on the maintained community module, Gemini on the stable one, the
coordinates `io.github.maxtrezzi:modelrack4j-*`, and — from the spike — the symlink watch
strategy ADR-0013 got wrong.

Three of the eight verification tasks **refuted the premise they were written with**: the GLM
module was not removed, token estimation was not OpenAI-only, and resolving symlinks to their
real path does not see a ConfigMap swap. That is the phase working as intended.

**v1 is complete.** M0 through M5 are done: layered configuration, capability-aware
validation, four providers, hot reload atomic across the whole snapshot, and — as of M5 — a
README and CHANGELOG, with the installed `0.1.0-SNAPSHOT` artifacts proven consumable by a
throwaway project outside the reactor.

**M6 was unscheduled, its trigger fired on 2026-09-02, and it finished the same evening.** The
owner tested the library, judged it publishable, and `0.1.0` went to Maven Central: seven
artifacts signed and published, `modelrack4j-examples` excluded and confirmed absent from both
the Portal and `repo1.maven.org`, and the whole thing resolved back from an empty local
repository to prove the download comes from Central. The `v0.1.0` tag is created on `main`
after the merge, never before the publish.

**Nothing rests on unexercised code any more.** `mvn -Pintegration verify` reached a live API
on 2026-08-24 ([P6](post-v1.md#p6--the-integration-tests-against-live-apis)): all four
providers answered a real request. It cost two stale model IDs and produced one portability
finding, [ADR-0033](../adr/0033-provider-exceptions-pass-through-untranslated.md). What it
leaves behind is a standing cost rather than an open item — model IDs rot, and two of the four
are now outside upstream's enums, so only a live run can catch the next one.

**The repository became public on 2026-08-25, and stayed unreleased on purpose**
([D2](open-decisions.md#d2--repository-visibility),
[ADR-0034](../adr/0034-the-repository-is-public-before-it-is-released.md)). The source and
the reasoning are readable. Public without released held for eight days: the version was
`0.1.0-SNAPSHOT` with no tag, no release and no artifact until M6's trigger fired on
2026-09-02. Two habits became rules with that switch:
`brainstorm/` is now a confidentiality boundary rather than a convention, and secret
discipline is pre-push rather than pre-release.

**The first outside reading of the public repository is closed**
([P7](post-v1.md#p7--closing-out-the-outside-review-of-the-public-repository)). Ten findings;
two were wrong about the code and wrong in the project's favour, two forced decisions
([ADR-0035](../adr/0035-ship-a-notice-file-for-attribution.md) adds a `NOTICE` file, and
`CLAUDE.md` was untracked and then put back within hours —
[ADR-0037](../adr/0037-claude-md-is-tracked-and-maintained.md) supersedes
[ADR-0036](../adr/0036-claude-md-is-local-only.md), because hiding a file does not stop it
drifting), and the rest were documentation the code had already outgrown.

**Two decisions are open again**, both raised by the first consumer putting `0.1.0` behind
HTTP: [D5](open-decisions.md#d5--a-version-token-for-optimistic-concurrency), whether a layer
should have a version token smaller than its whole text, and
[D6](open-decisions.md#d6--cannot-store-is-not-your-configuration-is-invalid), whether "cannot
store" deserves its own exception. Both wait on the owner. One thing waits on hardware
instead: the macOS half of
[Task 0.8](phase-0-verification.md#task-08--watch-strategy-spike). The README states that gap
rather than papering over it.

**Configuration no longer has to be a file, and a layer the application owns can be written
back** ([P19](post-v1.md#p19--configuration-sources-and-a-reload-the-application-can-ask-for),
[ADR-0042](../adr/0042-read-configuration-from-sources-not-files.md);
[P20](post-v1.md#p20--writing-a-configuration-layer-back),
[ADR-0044](../adr/0044-store-a-layer-back-as-text-validated-before-it-is-stored.md)). A layer
is text and a label, so it can be a database row; files keep their watcher, and anything else
asks for a reload. `store(target, text)` closes the loop the other way: it validates the new
text against the whole configuration, applies it, and only then stores it, so a text that
would not load is refused before it is saved rather than after. P20 built a fluent per-key
edit API first and removed it — the library had an opinion about *what* to write, and only
the ordering was ever worth owning.

**The tutorial's step 7 printed output its own command cannot produce**
([P26](post-v1.md#p26--two-documentation-gaps)). `mvn -q … exec:java` runs the example inside
Maven's process, and `-q` sets the SLF4J level there to `error`, so the library's `WARN` for a
rejected reload never reaches the terminal — while the page showed it. The `WARN` itself was
real; it came from a run of a command the page does not print. Both halves of P26 were
reproduced here before either was written down.
