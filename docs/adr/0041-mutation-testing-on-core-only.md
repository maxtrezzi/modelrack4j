# ADR-0041: Run mutation testing on core only, never on a provider module

- **Status:** Accepted
- **Date:** 2026-08-28
- **Supersedes:** —
- **Amends:** —

## Context

This library's public promises are largely *negative*: nothing swaps when any part of a
reload fails ([ADR-0012](0012-reload-atomicity-is-snapshot-wide.md)), one callback per
reload and never one per name
([ADR-0029](0029-reload-callbacks-are-quiet-contained-and-not-a-heartbeat.md)), an identical
snapshot notifies nobody, an unchanged block keeps the same instance
([ADR-0006](0006-named-configurations-with-per-name-diffing.md)), the opt-in flag is inert
rather than an error on a local counter
([ADR-0027](0027-remote-token-counting-is-opt-in.md)).

A test that asserts "nothing happened" passes when the code is broken in the direction that
also makes nothing happen. Line coverage cannot distinguish the two: it records that a line
ran, not that anything checked its result. Mutation testing can — it breaks the line and
asks whether any test notices.

Two properties of this build make it affordable here. The default build is offline and needs
no API keys, which is what lets a suite run hundreds of times; and `modelrack4j-core` holds
all of the logic that the negative promises are about, in 14 classes.

## Forces

**The provider modules cannot take part, and the reason is money.** Each of them carries an
`*IT.java` that calls a real, paid API under `-Pintegration`
([P6](../tasks/post-v1.md#p6--the-integration-tests-against-live-apis)). Mutation testing
runs the covering tests once per mutant, so pointing it at a module whose suite can reach a
billed endpoint turns a build into an invoice. The second reason is weaker but points the
same way: a provider module is almost entirely builder wiring, so its mutants are the arid
kind that can be killed only by asserting on the builder call itself.

**Line coverage would have answered a different question.** Adding JaCoCo was considered and
rejected: it reports which lines ran, which is exactly what the negative promises make
useless. Nothing here needed to know that the reload path executes; the question was whether
anything would object if it executed wrongly.

**A paid mutation engine is not warranted at this size.** Arcmutate and the other commercial
PIT extensions were considered and rejected: free PIT covers 14 classes without difficulty,
and the value being sought is a list of survivors to read, not throughput.

**The score is the wrong target, and the survivors are the right one.** A mutation score can
be raised by asserting on things that do not matter, which makes the suite longer and no
stronger. What the tool is being bought for is a list of places where breaking the code
changes nothing — each of which is a question about the tests, answered by writing a test,
excluding the code, or deciding the mutant is equivalent.

**Two areas produce timeouts or noise rather than information.** `ConfigWatcher` is built on
`WatchService` and a ~300 ms debounce ([ADR-0013](0013-watch-directories-resolve-symlinks.md),
[ADR-0024](0024-watch-the-symlink-s-directory-not-its-real-path.md)); mutating a timing
condition there produces a hung minion, not a finding. `toString` is a debugging aid whose
format nothing promises, so killing its mutants means pinning a format against every later
improvement.

**Whether this belongs in CI is a separate question, and could not be answered first.** The
run takes minutes, and [ADR-0040](0040-protect-main-with-required-checks-not-required-review.md)
makes every required check a gate every pull request waits on. The duration was unknown until
the tool had been configured and run, so adding it to `build.yml` is deliberately not part of
this decision.

## Decision

`org.pitest:pitest-maven` is configured **in `modelrack4j-core/pom.xml` and nowhere else**,
with `pitest-junit5-plugin` as a plugin dependency. Both versions are pinned in that module's
own `<properties>`, not the parent's, because no other module runs it.

- **Not bound to any lifecycle phase.** It runs only when its goal is named:
  `mvn -pl modelrack4j-core org.pitest:pitest-maven:mutationCoverage`. `mvn clean install`
  behaves exactly as it did before.
- **Never with `-Pintegration`.**
- **`targetClasses`** names both packages explicitly — `io.github.maxtrezzi.modelrack4j.*`
  and `io.github.maxtrezzi.modelrack4j.spi.*`. PIT's `*` is not recursive.
- **`excludedClasses`** is `io.github.maxtrezzi.modelrack4j.ConfigWatcher*`, with the
  trailing `*`.
- **`excludedMethods`** is `toString`.
- **`mutationThreshold` is `0`.** The build is not failed on the score. The deliverable is
  the survivor list in `target/pit-reports/`.
- **PIT is not in `.github/workflows/build.yml`**, and adding it needs its own decision.

## Consequences

**Gained.** A check no other tool in this repository performs: whether a test would object
if the code stopped doing what the test claims to verify. Its first run
([P17](../tasks/post-v1.md#p17--mutation-testing-on-core)) found four defects in the suite,
including a test whose name described the provider-side validation contract while its
assertion was satisfied by an unrelated failure downstream — so the SPI contract in
[ADR-0005](0005-provider-factory-spi-via-serviceloader.md) was defended by nothing.

**Accepted.** The run is measured in minutes rather than seconds, it is invoked by hand, and
nothing enforces that anyone invokes it. That is the deliberate consequence of leaving it out
of CI until the cost is known; the alternative was to gate every pull request on a number
nobody had yet.

**Do not move the plugin into the parent POM.** Declaring it in the parent's `<build>`
`<plugins>` would inherit it into every module, the four provider modules included. Because
the goal is never bound to a phase, this would look harmless in `mvn clean install` and cause
nothing until someone runs `mutationCoverage` from the reactor root, at which point the
provider suites become mutation targets. The blast radius of that tidy-up is a bill, not a
red build.

**Do not drop the `*` from the `ConfigWatcher*` exclusion.** The filename filtering lives in
the nested `WatchedDirectory` class, which a bare class name does not match. The first run of
this configuration made exactly that mistake and produced three timeouts from it.

**This proves nothing about the concurrency guarantee.** Mutants are deterministic syntactic
edits; they do not explore thread interleavings, so nothing PIT reports bears on the promise
that a concurrent reader never observes a mixed pair of bundles
([ADR-0038](0038-snapshot-gives-callers-the-atomicity-the-swap-already-has.md)). A high
mutation score must not be read as evidence for it. The tool for that question is a
concurrency stress harness such as OpenJDK's jcstress, which this project does not have.

**Foreclosed.** Nothing permanently. Raising `mutationThreshold` above `0`, or adding PIT to
the CI matrix, are later decisions that this one deliberately leaves open; either would need
its own reasoning about what a failing score should mean for a pull request.
