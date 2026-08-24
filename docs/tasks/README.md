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
- [`milestones.md`](milestones.md) — M0–M5. What ships, in what order.
- [`open-decisions.md`](open-decisions.md) — items blocked on the owner, not on work.
- [`post-v1.md`](post-v1.md) — P1, P2, … work taken on after v1 closed.

## Conventions

**Identifiers never change.** `Task 0.4` and `M3` are cited from `CLAUDE.md` and from the
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
| [P1](post-v1.md#p1--console-chat-example) | Console chat example | **Done** — reload seen live |
| [P2](post-v1.md#p2--a-short-description-per-configuration) | A short description per configuration | **Done** — ADR-0032 |
| [P3](post-v1.md#p3--the-manual) | The manual: tutorial and reference | **Done** — every command run |
| [P4](post-v1.md#p4--two-examples-for-the-two-undemonstrated-strengths) | Examples for the two undemonstrated strengths | **Done** — atomicity shown, and made to fail |
| [D1](open-decisions.md#d1--glm-route-if-no-maintained-module-exists) | GLM route if no maintained module | **Closed** — never became live |
| [D2](open-decisions.md#d2--repository-visibility) | Repository visibility | Needs decision |
| [D3](open-decisions.md#d3--token-window-memory-on-a-remote-estimator) | Token-window memory on a remote estimator | **Settled** — opt-in flag |

**Phase 0 is complete except for one measurement, and M0 is done — the build is green.** Tasks 0.1–0.7 are
done; Task 0.8 is done on Linux and open only on the macOS latency figure, which qualifies
documentation rather than changing code and so gates neither M0 nor M3.

What the phase settled: the pin at `1.19.0` and the two-BOM import it later needed, `release`
17 as the compile target, the type names and the one mismatch ADR-0020 resolves, the
capability matrix, GLM on the maintained community module, Gemini on the stable one, the
coordinates `io.github.maxtrezzi:modelrack4j-*`, and — from the spike — the symlink watch
strategy ADR-0013 got wrong.

Three of the seven verification tasks **refuted the premise they were written with**: the GLM
module was not removed, token estimation was not OpenAI-only, and resolving symlinks to their
real path does not see a ConfigMap swap. That is the phase working as intended.

**v1 is complete.** M0 through M5 are done: layered configuration, capability-aware
validation, four providers, hot reload atomic across the whole snapshot, and — as of M5 — a
README and CHANGELOG, with the installed `0.1.0-SNAPSHOT` artifacts proven consumable by a
throwaway project outside the reactor.

**Nothing is scheduled after it.** M6 (GPG signing, Central Portal publishing) is deliberately
unscheduled: it is triggered by the library proving itself in the owner's first real project,
not by a date.

Three things are open, and none of them is code:

- **[D2](open-decisions.md#d2--repository-visibility)** — needs the owner. The only open
  decision.
- **`mvn -Pintegration verify` has never reached a live API.** Both guards are verified across
  all four providers; the payload has never run. Worth doing once, and it is now the last
  claim in the README that rests on unexercised code.
- **The macOS half of [Task 0.8](phase-0-verification.md#task-08--watch-strategy-spike)** —
  needs hardware. The README states the gap rather than papering over it.
