# ADR-0061: Work lands on `dev`; `main` carries releases only

- **Status:** Accepted
- **Date:** 2026-09-07
- **Supersedes:** —
- **Amends:** [ADR-0016](0016-one-feature-branch-per-task.md) and [ADR-0040](0040-protect-main-with-required-checks-not-required-review.md)

## Context

Until `0.2.0` every task merged straight into `main`. That made `main` a mixture: the last
released version plus whatever had landed since. Three things follow from it, and all three
were felt while releasing `0.2.0`.

Somebody arriving at the repository reads `main`. What they read was not what
`io.github.maxtrezzi:modelrack4j-*` would give them, and nothing on the page said so — the
README's own dependency snippets pointed at the released version while the code beside them
was ahead of it.

Several items that belong to one version could not be tried together before becoming one.
`0.2.0` merged P40 through P45 one at a time, each one green on its own; whether they were
right *together* was only ever answered by the release itself.

And `main`'s history carried one commit per task, so reading it to answer "what changed
between the two releases" meant reading a dozen commits and deciding which were user-visible.

## Forces

**A default branch is where a reader lands, and where a contributor branches from.** Whatever
`main` means has to be true without explanation, because nobody reads a note about it first.

**The gate is the five required checks, not the branch name.** Moving work to a branch the CI
does not build would replace review with nothing. `.github/workflows/build.yml` triggers on
`main` alone, and the five required contexts are configured on `main`'s protection; a pull
request against an unlisted branch runs no job and reports no status.

**A squash merge does not relate two branches.** This repository squashes, so a `dev` squashed
into `main` gives `main` a commit that exists nowhere in `dev`. The two trees agree at that
instant; their histories never do.

**Two long-lived branches are a standing chance to diverge.** The failure is not theoretical:
it is somebody branching from `main` out of habit, working, and finding every conflict twice.

## Decision

1. **`dev` is the default branch.** Every task branches from it and opens its pull request
   against it, under ADR-0016's naming unchanged.
2. **`main` receives releases only.** One commit per released version, each carrying its tag,
   so `main`'s tree is always what Maven Central holds. Between releases `main` does not move.
3. **A release is a pull request from `dev` to `main`, squashed**, whose subject is the
   version. The version commit — the eight POMs, the CHANGELOG heading,
   `project.build.outputTimestamp` — lands on `dev` first, like any other work.
4. **`main` is never merged back into `dev`.** After a release the two hold the same tree and
   unrelated histories, and `dev` simply carries on. Never branch from `main`, never rebase
   `dev` onto it.
5. **Both branches are protected, with the same five required checks**, and
   `.github/workflows/build.yml` triggers on both. `enforce_admins` stays `false` for the
   reason ADR-0040 gives.

## Consequences

**`main` becomes readable as a list of releases.** `git log main` is the version history, and
`git log main..dev` is exactly what an unreleased version would contain — which is the question
the CHANGELOG's `Unreleased` section answers in prose.

**The release sequence gains a step and loses a hazard.** Publishing now runs from `main` after
the release pull request merges, so the artifacts are built from the tree that carries the tag,
rather than from a tree that has moved since. P45 recorded the `0.2.0` sequence, which
published from `main` when `main` still held everything; the shape survives, with `dev` as the
place the version commit is prepared.

**A contributor's pull request targets `dev`, and GitHub now proposes that by default.**
`CONTRIBUTING.md` says so; the default branch is what makes it true without being read.

**The cost is the fourth point, and it is a real one.** Nothing enforces "never merge `main`
into `dev`" — the protection rules cannot express it. It is a rule kept because it is right,
the same trade ADR-0040 already accepts, and it is written here because the session that
breaks it will be the one that did not know.
