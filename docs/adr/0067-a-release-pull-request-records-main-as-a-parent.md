# ADR-0067: A release pull request records `main` as a parent

- **Status:** Accepted
- **Date:** 2026-10-28
- **Supersedes:** —
- **Amends:** [ADR-0061](0061-work-lands-on-dev-and-main-carries-releases.md)

## Context

ADR-0061 makes a release a pull request from `dev` to `main`, squashed. A squash gives `main` a
commit that exists nowhere in `dev`, and ADR-0061 says so: after a release the two branches hold
the same tree and unrelated histories.

`0.3.0` did not show what that costs. `main` then held only commits that were also in `dev`'s
history, because `dev` had been created from `main` after `0.2.0`, so `0.3.0`'s pull request
merged cleanly. `0.4.0` was the first release with a squash commit on `main` that `dev` does not
have: `383a83d`, the squash of `0.3.0`. The pull request from `dev` (#86) came back
`CONFLICTING` on 36 files, although `383a83d`'s tree is the same as `dev`'s `a23f455`, so `main`
held nothing `dev` lacked. The merge base was still `af58c81`, from `0.2.0`, and Git saw two
different sets of changes since then to the same lines.

This happens again at every release from now on.

## Forces

- **`main` must not be merged into `dev`, and nothing may branch from `main`** (ADR-0061, point 4).
  Merging `main` into `dev` once would fix the conflict for one release. It would also make
  `dev`'s history depend on `main`'s, which is what the rule forbids.
- **Rebasing `dev` onto `main`, or force-pushing either branch,** rewrites shared history, and
  the protection blocks both.
- **A branch from `main` whose tree is replaced with `dev`'s** gives the same result, but it
  breaks "never branch from `main`" as written, and its diff against `main` cannot be read as a
  merge.
- **A branch from `dev` with `git merge -s ours main`** keeps `dev`'s tree exactly. `main`
  becomes a second parent and no content of `main` is taken. The pull request from that branch
  merges cleanly, and its squash onto `main` is one commit with `dev`'s tree. The branch is used
  once and never goes back into `dev`.

## Decision

1. **The head of a release pull request is `release/<version>-main`**, created from `dev` after
   the version commit, carrying one commit: `git merge -s ours origin/main`.
2. **Before pushing it, check two things**: `git diff origin/dev HEAD` is empty, and
   `git merge-tree --write-tree origin/main HEAD` reports no conflict.
3. **The pull request is squashed onto `main`** with the version as its subject, as ADR-0061
   says, and the branch is deleted afterwards. It is never merged into `dev`.

Everything else in ADR-0061 stands.

## Consequences

- **A release no longer conflicts**, however many releases `main` holds. `main` still gets one
  commit per version with exactly `dev`'s tree.
- **`dev` is never touched.** Its history never contains a commit from `main`, and
  `git log main..dev` still lists what an unreleased version contains.
- **The release sequence has one more branch.** It is mechanical, and point 2 checks it. A
  pull request opened from `dev` directly will conflict. That is a sign this step was skipped,
  not a reason to merge `main` into `dev`.
- **The ours merge is a merge of `main`**, in a branch that only `main` receives. It does not
  break "never merge `main` into `dev`", because the branch never reaches `dev`.
