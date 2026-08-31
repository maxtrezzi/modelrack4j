# Open decisions

Items waiting on the owner rather than on work. Do not resolve these unilaterally — each
one closes by writing an ADR (see [ADR-0001](../adr/0001-record-decisions-as-adrs.md)).

**D1 to D4 are all settled**, so this file is a record rather than a queue right now. A
new entry here is a question for the owner, not work to pick up. Entries stay in number order
and keep the framing they were decided under, with the outcome at the top.

---

### D1 — GLM route if no maintained module exists

**Status:** Closed 2026-08-20 without a ruling — never became live ·
**Settled by:** [Task 0.3](phase-0-verification.md#task-03--glm-module-status)

Task 0.3 found `langchain4j-community-zhipu-ai` maintained and current at `1.19.0-beta29`;
the "removed in 1.3.0" report was a beta-suffix misreading, answered by the project lead on
the upstream issue itself. The native module is used and the fallback below is not taken —
[ADR-0022](../adr/0022-glm-via-the-community-module-and-its-bom.md), which also records the
second BOM import the module requires and the beta-line dependency it entails.

M4 is no longer blocked on this. The provider set is unchanged, so
[ADR-0005](../adr/0005-provider-factory-spi-via-serviceloader.md) needs no amendment.

**As posed, now the rejected alternative.** Had no maintained module existed, the fallback
was a `GlmProviderFactory` built on `langchain4j-open-ai` pointed at Zhipu's
OpenAI-compatible endpoint: it works, but GLM-specific parameters are unreachable through
the OpenAI-shaped API, and the library would be depending on an endpoint compatibility
guarantee it does not control. Kept here because ADR-0022 forecloses it deliberately — the
beta suffix on the native module is not a reason to revisit this.

---

### D2 — Repository visibility

**Status:** Settled 2026-08-25 — **public now, not released** ·
**Settled by:** [ADR-0034](../adr/0034-the-repository-is-public-before-it-is-released.md)

The owner made the repository public on 2026-08-25, taking the *public now* option below.
The decision is narrower than the switch it was made with: public and released are separate
acts, and only the first was taken. The version stays `0.1.0-SNAPSHOT`, there is no tag, no
GitHub release and no announcement, and M6 keeps its own trigger unchanged. ADR-0034 records
why, and what being readable now makes non-negotiable — `brainstorm/` as a confidentiality
boundary rather than a convention, secret discipline that is pre-push rather than
pre-release, and history that can no longer be quietly rewritten.

The framing the decision was taken under follows.

Public from day one, or public at first release?

This became live rather than theoretical once the repository was initialised and its first
commit landed. Two things depend on it:

- **JitPack** requires a public repository. It is the intermediate distribution option if
  the library needs sharing before it reaches Maven Central.
- **The ADRs are written to be read.** Their value as a public rationale record — a large
  part of what makes an independent library credible
  ([ADR-0002](../adr/0002-scope-to-langchain4j-llm-configuration.md),
  [ADR-0011](../adr/0011-independent-name-and-deferred-wrapper.md)) — only materialises
  when someone outside the project can read them.

**Corrected 2026-08-20:** the "nothing has left the machine" premise this entry was written
under no longer holds. A remote *is* configured — `github.com/maxtrezzi/modelrack4j`, created
2026-07-26 — and the work to date has been merged through pull requests. It is **private**,
confirmed in [Task 0.7](phase-0-verification.md#task-07--name-and-coordinates).

That changes the framing, not the decision. The repository is on GitHub rather than only on
this machine, so "public from day one" is no longer available as an option — the live choice
is *public now* or *public at first release*. The one-way property still stands: private can
become public, but what has been public cannot be made unseen.

Task 0.7 also removed one thing that might have forced the answer: Maven Central publishing
does **not** require this repository to be public, because namespace verification uses a
separate temporary repository ([ADR-0025](../adr/0025-fix-coordinates-under-io-github-maxtrezzi.md)).
JitPack and the readability of the ADRs remain the two real arguments.

**On settling:** write an ADR, and if the answer is "public at first release", note what
triggers the switch so it does not drift.

---

### D3 — Token-window memory on a remote estimator

**Status:** Settled 2026-08-21 — **opt-in** ·
**Raised by:** [Task 0.6](phase-0-verification.md#task-06--provider-capability-matrix) ·
**Settled by:** [ADR-0027](../adr/0027-remote-token-counting-is-opt-in.md)

The owner chose the opt-in option below. `memory.type = token-window` on a `REMOTE`-estimator
provider fails validation unless `memory.allow-remote-token-counting = true` is set; the
default is `false`, and the failure message must name the flag. `ABSENT` (GLM) is not
escapable. On `LOCAL` providers the key is permitted and inert, so a config layer spanning
several providers need not be split. M2 unblocked.

[ADR-0021](../adr/0021-token-estimation-is-universal-but-two-cost-classes.md) established
that all four providers ship a `TokenCountEstimator`, but only OpenAI's counts locally.
Anthropic's and both Gemini ones make an HTTP call, with an API key and a timeout.

`TokenWindowChatMemory` calls the estimator on eviction. So `memory.type = token-window` on
those three providers puts a billed, rate-limited, failure-prone network call into ordinary
conversation turns — inside a component applications reasonably assume is local bookkeeping.

ADR-0021 fixes the *capability model* (three-valued, so validation can tell the cases apart)
but deliberately not the *policy*, because each option changes the config schema, which is
the plan's to change and not an ADR's:

- **Reject** — `validate()` fails the configuration. Safest, and wrong for anyone who
  genuinely wants accurate remote counts and has priced it in.
- **Warn** — build it, log loudly once. Nothing is forbidden, but a warning in a log is a
  weak signal for a per-turn cost, and this project's whole posture is fail-fast validation
  ([ADR-0008](../adr/0008-fail-fast-validation-staged-build-atomic-swap.md)).
- **Opt-in** — reject unless the config says so explicitly, e.g. a
  `memory.allow-remote-token-counting` flag. Fail-fast by default, escapable on purpose.
  Costs one schema key, and [ADR-0010](../adr/0010-discriminators-only-with-two-real-variants.md)
  is hostile to keys that do not earn their place.

**Blocks:** M2 (memory construction) and the `validate()` implementations in M4.

**On settling:** write an ADR; if the answer adds a config key, the schema in the plan
changes with it.

---

### D4 — Mutation testing in CI

**Status:** Settled 2026-08-31 — **never** ·
**Raised by:** [ADR-0041](../adr/0041-mutation-testing-on-core-only.md) ·
**Settled by:** [ADR-0043](../adr/0043-keep-mutation-testing-out-of-ci.md)

ADR-0041 configured PIT on core and refused to decide this one, for a stated reason: the run
took an unknown time, and
[ADR-0040](../adr/0040-protect-main-with-required-checks-not-required-review.md) makes every
required check a gate that every pull request waits on. The owner chose **never**,
in any form — not a required check, not an optional job, not a nightly.

The measurement that was missing is now taken: a full run on core is **122 s** on the
development machine, against **about 42 s** for the whole current gate, whose five checks run
in parallel and whose slowest leg was 42 s in run `33320243644`. So PIT would roughly triple
what a pull request waits for.

But duration turned out not to be the deciding argument. Two of the 153 mutants cannot be
gated on at all: one is *equivalent* and unkillable by construction, so a 100 % threshold is
permanently red, and one is a deliberate timeout whose outcome depends on how loaded the
machine is. `mutationThreshold` is `0`, so a job would be permanently green and certify
nothing. And the deliverable ADR-0041 fixed is a survivor list to read, which CI has nobody to
read.

**The rejected alternative was a nightly**, not a required check: no gate cost, no pass-or-fail
problem, the report published as an artifact. It loses because nobody is obliged to read it,
and because it still puts `mutationCoverage` in a file — and the only thing keeping PIT away
from the provider modules and their paid `*IT.java` suites is that a person types
`-pl modelrack4j-core`.
