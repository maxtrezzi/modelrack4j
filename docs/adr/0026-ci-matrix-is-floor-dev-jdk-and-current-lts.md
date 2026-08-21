# ADR-0026: The CI matrix is the floor, the development JDK, and the current LTS

- **Status:** Accepted
- **Date:** 2026-08-20
- **Supersedes:** —
- **Amends:** ADR-0019 — the CI matrix gains a third leg

## Context

[ADR-0019](0019-target-java-17.md) fixed `maven.compiler.release` at 17 and required CI to
verify on **17 and 21** — *"17 because it is the claimed floor and nothing else proves the
floor holds, 21 because that is what development happens on and what most consumers will
actually run."*

Two legs, two distinct jobs: one guards the floor, one matches reality. The reasoning is
sound and unchanged. What changed is a fact underneath it — **development no longer happens
on 21.** [Task 0.8](../tasks/phase-0-verification.md#task-08--watch-strategy-spike) found the
machine running Temurin **25.0.3**, and `CLAUDE.md`'s claim of JDK 21 was corrected in the
same change.

So the "development JDK" leg and the "21" leg have come apart, and
[M0](../tasks/milestones.md#m0--skeleton-and-ci) — which asks for CI green on *"the baseline
JDK and the latest LTS"* — needed the question settled before the workflow was written.

## Forces

- **Dropping 21 for 25** keeps the matrix at two legs and preserves ADR-0019's shape exactly.
  But 21 is an LTS in wide production use, and the project would stop testing the version a
  large share of consumers actually run — trading real coverage for tidiness.
- **Keeping only 17 and 21** ignores the JDK the code is now written and run on daily, which
  is precisely the leg ADR-0019 argued hardest for.
- **A third leg costs CI minutes**, and matrices grow without anyone deciding to grow them.
  That is a real cost, and the reason this ADR names what each leg is *for* — a leg that
  cannot answer "which of the three jobs is this?" should not be added.
- `release` 17 means all three legs compile identical bytecode, so the extra legs test the
  *toolchain and runtime*, not the language level. That is cheap and still worth having:
  javadoc, surefire and the enforcer plugins all behave differently across JDKs.

## Decision

**CI builds on JDK 17, 21 and 25**, each leg with a stated job:

| Leg | Why it is there |
|---|---|
| **17** | The claimed floor. Nothing else proves the floor holds. |
| **21** | The most widely deployed LTS among likely consumers. |
| **25** | The development JDK, and the current LTS. |

The matrix runs `mvn verify`, not `package` — the enforcer's convergence rule, the
license-header check and the Javadoc build are all part of what "green" means.

**A separate job builds with provider credentials explicitly emptied**, so that a test which
quietly depends on an API key fails in CI rather than in a contributor's fork.

**When the development JDK moves again, the 25 leg moves with it** — it tracks the machine,
not the number. If that ever collides with the 21 leg, drop to two legs rather than keeping
a leg whose job is already covered.

## Consequences

- ADR-0019's floor guarantee is untouched: `release` stays 17 and the 17 leg still proves it.
- The matrix is self-documenting — each leg's purpose is written into the workflow, so the
  next person can tell whether a leg is still earning its place instead of guessing.
- **Do not add a leg per new JDK release.** Non-LTS versions are not covered deliberately;
  `release` 17 makes them uninteresting for a library that ships Java 17 bytecode.
- Adding the 25 leg is what caught nothing yet, and that is the expected outcome — its value
  is negative evidence, produced continuously and cheaply.
