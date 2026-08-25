# ADR-0036: `CLAUDE.md` is local-only; the tracked documentation is the source

- **Status:** Superseded by [ADR-0037](0037-claude-md-is-tracked-and-maintained.md)
- **Date:** 2026-08-25
- **Supersedes:** —
- **Amends:** —

## Context

The 2026-08-25 review flagged `CLAUDE.md`'s presence in the now-public repository
([ADR-0034](0034-the-repository-is-public-before-it-is-released.md)) — not as a defect,
since publishing one is ordinary and increasingly common, but as something that should be a
choice rather than an oversight, given that the repository doubles as a portfolio artifact.

The owner chose to remove it.

`CLAUDE.md` is operating instructions for one contributor's toolchain. It is not consumer
documentation, and it is not the reasoning record — those are `README.md`, `docs/manual/`,
`docs/adr/` and `docs/tasks/`, each of which has an audience and a job.

## Forces

- **It restates the tracked documents in summary form, so it can drift against them.** It
  already has: [Task 0.2](../tasks/phase-0-verification.md#task-02--verify-the-java-baseline)
  recorded that `CLAUDE.md`'s toolchain note claimed JDK 21 when the machine ran Temurin 25,
  and it had to be corrected across `CLAUDE.md` and the ADRs in the same change. A summary
  that must be maintained in lockstep with its source is a liability the moment it is public,
  because a reader cannot tell which copy is stale.
- **Against removal — orientation for contributors.** Real, but addressed: `CONTRIBUTING.md`
  landed in [P5](../tasks/post-v1.md#p5--repository-hygiene-ignore-rules-and-a-contributing-guide)
  and `docs/tasks/README.md` is the entry point for what to do next. Neither depends on
  `CLAUDE.md` existing.
- **Against removal — it is a genuinely interesting artifact.** Also real. It loses to the
  drift argument, and nothing prevents publishing a written-for-readers version later if that
  is ever wanted; that would be a document with an audience rather than a working file.
- **Removal is forward-looking only, and this must not be overstated.** `CLAUDE.md` has been
  tracked since the first commit and appears in six of them. Untracking it removes it from
  the tree, not from the history, and the history is public. Anyone can still read every
  version of it. This decision is about what the repository *presents*, not about
  retraction — no history is rewritten, and rewriting it would be the wrong trade for a file
  whose contents are not sensitive.

## Decision

**Untrack `CLAUDE.md` and add it to `.gitignore`, alongside `brainstorm/`.**

The file stays on the working machine and keeps doing its job. The tracked documents remain
the only published account of the project, which is what they were written to be.

This does **not** extend to anything else under `.claude/`: the shared `settings.json` stays
tracked, and `settings.local.json` was already ignored.

## Consequences

- The public repository presents one coherent set of documents, each with a stated audience,
  and no working file that is really addressed to a tool.
- Guidance and tracked documentation can no longer contradict each other in public, because
  only one of them is public.
- A contributor cloning the repository gets `CONTRIBUTING.md` and `docs/tasks/` rather than
  `CLAUDE.md`. If that proves thin, the fix is to improve `CONTRIBUTING.md` — a document
  written for people — not to re-track a working file.
- **[ADR-0025](0025-fix-coordinates-under-io-github-maxtrezzi.md) now contains a dead link.**
  Its Consequences section links `[CLAUDE.md](../../CLAUDE.md)`, and that target no longer
  exists in the tree. It is deliberately **not** fixed: accepted ADR bodies are frozen, and
  the freeze is worth more than the link. A reader who follows it and finds nothing learns
  something true — that the file existed and was cited when ADR-0025 was written in August
  2026 — which is exactly what a contemporaneous record is for. Do not "repair" it.
- The same is true, less visibly, of the plain-text mentions in
  [ADR-0015](0015-track-work-items-in-docs-tasks.md),
  [ADR-0026](0026-ci-matrix-is-floor-dev-jdk-and-current-lts.md) and two places in
  `docs/tasks/`. Those are references, not links, and they stay as they are for the same
  reason.
