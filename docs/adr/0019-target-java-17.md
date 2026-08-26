# ADR-0019: Target Java 17, build on a newer JDK

- **Status:** Accepted — CI matrix amended by [ADR-0026](0026-ci-matrix-is-floor-dev-jdk-and-current-lts.md)
- **Date:** 2026-08-03
- **Supersedes:** —
- **Amends:** —

## Context

[Task 0.2](../tasks/phase-0-verification.md#task-02--verify-the-java-baseline) asked what
Java version the pinned LangChain4j `1.18.0` ([ADR-0018](0018-manage-langchain4j-versions-via-bom.md))
requires, so `maven.compiler.release` can be set from evidence rather than assumption.

LangChain4j `1.18.0` requires **Java 17**, confirmed three independent ways: every class in
`langchain4j-core` and in all four provider artifacts carries bytecode major version 61 with
no multi-release overlays; upstream's `langchain4j-parent` POM at tag `1.18.0` sets
`<java.version>17</java.version>` and derives `maven.compiler.release` from it; and the
upstream documentation states "The minimum supported JDK version is 17." The transitive
dependencies impose nothing higher — Jackson, SLF4J and JSpecify all ship Java 8 base
classes.

So upstream does not force the answer above 17, and the floor this project already had —
records are used throughout — is exactly 17. The question is therefore not what is
*permitted* but what to *choose*, since the development machine runs JDK 21 and a library
may target higher than its dependencies do.

## Forces

- **The consumer sets the ceiling, not the author.** This is a library, and its compile
  target is a hard requirement it imposes on every application that adopts it. Java 17 is
  still widely deployed; targeting 21 would exclude those applications for no functional
  gain.
- **Raising the target later is cheap; lowering it is a breaking change.** A consumer on
  Java 17 that can no longer link is broken by a minor release. Starting at 17 keeps the
  option open in both directions; starting at 21 spends it immediately.
- **Java 21 offers real things** — virtual threads, pattern matching for switch, sequenced
  collections. But none of them are load-bearing for this design: the work is config
  parsing, validation, and an atomic reference swap. Nothing here is thread-per-request or
  needs structured concurrency.
- **Against 17:** the code cannot use 21-only idioms, and contributors on 21 will
  occasionally reach for one and be stopped by the compiler. `release` (not `source`/
  `target`) is what makes that a compile error rather than a runtime `NoSuchMethodError` on
  a consumer's 17 JVM.
- **Building on 17 to guarantee 17 is the alternative to `release`**, and it is worse: it
  pins the toolchain and drags in an old JDK's tooling. `maven.compiler.release` compiles
  against the 17 API signatures from a newer JDK, which is exactly the guarantee wanted.

## Decision

Set `maven.compiler.release` to **17** in the root POM, and let LangChain4j's own baseline
be tracked rather than guessed — if a future LangChain4j bump raises its minimum, that
raises this project's target too ([ADR-0018](0018-manage-langchain4j-versions-via-bom.md)
makes the bump one line, but this is not automatic and must be re-checked on upgrade).

Build with a newer JDK — 21 is what the development machine runs — and rely on `release`
rather than a matching toolchain for the guarantee. Use `release`, never `source`/`target`.

CI must verify on **both 17 and 21**: 17 because it is the claimed floor and nothing else
proves the floor holds, 21 because that is what development happens on and what most
consumers will actually run.

Java 21 idioms are not available. Language use stops at what Java 17 provides — records,
sealed types, and switch pattern matching only in its Java 17 form.

## Consequences

- The library links on any JDK 17 or newer, and that claim is tested rather than asserted.
- `release` turns an accidental Java 21 API call into a build failure in this repository,
  instead of a `NoSuchMethodError` in a consumer's application. That is the whole reason to
  prefer it, and it is why **`source`/`target` must not be substituted** — they check the
  language level but happily link against the building JDK's newer APIs.
- Virtual threads are unavailable. This is not a constraint the current design feels: the
  reload path is a single watch thread and an atomic swap, not a request-per-thread server.
  If a future version wants them, that is a deliberate baseline bump with a new ADR, not a
  quiet change to a property.
- The CI matrix is two JDKs rather than one — a real cost in build minutes, accepted
  because a floor that is never built against is a floor that silently rots.
- **Do not raise `maven.compiler.release` to match whatever JDK a contributor happens to
  run.** It looks like keeping current and behaves like dropping support for every consumer
  below that version, in a release that is not labelled as breaking.
