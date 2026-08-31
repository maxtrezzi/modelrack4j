# ADR-0043: Keep mutation testing out of CI, in every form

- **Status:** Accepted
- **Date:** 2026-08-31
- **Supersedes:** —
- **Amends:** [ADR-0041](0041-mutation-testing-on-core-only.md) — answers the CI question it
  left open

## Context

[ADR-0041](0041-mutation-testing-on-core-only.md) configured PIT on `modelrack4j-core` and
deliberately did not decide whether it belongs in CI. It gave a reason for not deciding: *"The
duration was unknown until the tool had been configured and run, so adding it to `build.yml`
is deliberately not part of this decision."*

The tool has since been configured and run several times, so the number exists. A full
`mutationCoverage` on core takes **122 s** on the development machine — JDK 25, warm `~/.m2`,
153 mutants. A GitHub-hosted runner is not going to beat a warm developer machine, so that is
a floor rather than an estimate.

The five checks [ADR-0040](0040-protect-main-with-required-checks-not-required-review.md)
requires before a merge were measured from run `33320243644`: docs consistency 5 s, JDK 17
42 s, JDK 21 40 s, JDK 25 39 s, offline 39 s. They run in parallel, so a pull request waits
about **42 s** today. A PIT job would run in parallel too, which means the gate stops being
42 s and becomes at least 122 s — about three times the wait, on every pull request,
including one that fixes a typo in the README.

## Forces

**What a CI job could plausibly buy.** ADR-0041 accepted that *"nothing enforces that anyone
invokes it"*. A job would surface survivors without anyone remembering to look, and a new
survivor is exactly the signal wanted when a change adds code the tests do not really check.
That is a real argument, and it is the only one on that side.

**The output is not pass or fail, and `mutationThreshold` is `0` by design.** A job at
threshold `0` can only ever be green. A green check that reports nothing is worse than no
check: it takes a slot in the required set and certifies nothing, while looking like
assurance. Making it fail needs a threshold, and ADR-0041's reasoning against one stands — a
score is raised by asserting on things that do not matter, which lengthens the suite without
strengthening it.

**The number a threshold would gate is not reachable anyway.** One of the 153 mutants is
*equivalent*: `EmptyObjectReturnValsMutator` rewrites `LlmRegistry.reload()`'s own
`return Optional.empty()` into itself, so the mutant and the original are the same bytecode
and no test can tell them apart
([P19](../tasks/post-v1.md#p19--configuration-sources-and-a-reload-the-application-can-ask-for)).
A 100 % threshold is permanently red. Anything lower is a number chosen to accommodate a known
artefact, which is a threshold that measures nothing.

**One mutant is a deliberate timeout, and a timeout is a measurement of how busy the machine
is.** `NullReturnValsMutator` makes `chooseNotifier` return null, the registry then watches
nothing, and a waiting test hangs — ADR-0041 calls it *"a hung minion, not a finding"*. PIT
counts `TIMED_OUT` as detected, so it is not a failure today; it is an outcome that depends on
load. A shared runner is the worst place to hold something like that.

**The deliverable needs a reader, and CI has none.** ADR-0041 fixed the deliverable as the
survivor list rather than the score. [P17](../tasks/post-v1.md#p17--mutation-testing-on-core)'s
most useful finding was a test whose *name* described the provider-side validation contract
while its assertion was satisfied by an unrelated failure downstream. No threshold expresses
that. It came from reading the list and asking what each survivor meant.

**The report is only usable on a settled tree.** P19 produced one while the source was still
being edited; its line numbers pointed at code that had moved, and the run had to be repeated
before any figure from it could be quoted. CI runs on every push, which is the least settled a
tree ever is.

**A workflow file would be a standing invitation to the one mistake that costs money.**
ADR-0041's blast radius is that PIT must never reach a provider module, because each carries
an `*IT.java` against a paid API and PIT runs the covering tests once per mutant. What stands
between that and an invoice today is that the command is typed by a person who knows to type
`-pl modelrack4j-core`. Put that command in a workflow and it becomes a line someone edits
later — and CI is precisely where nobody is watching for a bill.

**A scheduled job was considered and rejected as well.** A weekly workflow that is not a
required check, publishing the report as an artifact, avoids both the gate cost and the
pass-or-fail problem. It was rejected because it produces a report nobody is obliged to read,
about a tree nobody has a reason to be looking at, in a repository with one maintainer — and
it still puts the invocation in a file, which is the hazard above. A nightly is still CI.

## Decision

**PIT is never added to `.github/workflows/`, in any form.** Not as a required check, not as
an optional job, not on a schedule, not behind a label, not behind `workflow_dispatch`.

- `mutationThreshold` stays `0` (ADR-0041). It gates nothing, anywhere.
- The invocation stays the one ADR-0041 fixed, typed by a person:
  `mvn -pl modelrack4j-core org.pitest:pitest-maven:mutationCoverage`.
- **The trigger is human and named.** Run it when core's `src/main` or its tests changed, on a
  tree that has stopped moving, and read `target/pit-reports/`. A change that touches no
  `.java` file needs no run at all: mutants are generated from compiled classes, so the
  mutant list is identical by construction.
- This closes the question ADR-0041 left open. Its Consequences call adding PIT to the CI
  matrix *"a later decision that this one deliberately leaves open"*. This is that decision,
  and the answer is no.

## Consequences

**Accepted, and now permanent rather than provisional.** Nothing enforces that anyone runs
PIT. ADR-0041 accepted that while the cost was unknown. The cost is now known and the answer
does not change, so this stops being a gap awaiting data and becomes the settled state:
mutation coverage can regress between runs, and no build will say so.

**Gained.** The required set stays at five checks and about 42 s, which is what makes a green
build a usable gate for an outside contributor who gets no review (ADR-0040). No permanently
green ornamental job. No load-dependent mutant on a shared runner. And the guard against
mutating a paid provider suite stays a human command rather than a line in a file.

**What replaces enforcement is the worked examples.** P17 and P19 record what a run produces
and how to read it, and `CLAUDE.md` carries the rule about which modules PIT may touch. That
is a weaker mechanism than a check, chosen deliberately over a check that would certify
nothing.

**Do not add it "just as an optional job".** An optional job in `build.yml` is one
`required_status_checks` edit away from being a gate, and one deleted `-pl` away from
mutating the provider modules. Both of those edits look like tidying up.

**Foreclosed.** This is a "never" rather than a deferral, which is the difference between it
and ADR-0041's phrasing. Reversing it means superseding this ADR with an argument that
answers three things: what a threshold does about the equivalent mutant, what a shared runner
does about the timeout mutant, and who reads the survivor list.
