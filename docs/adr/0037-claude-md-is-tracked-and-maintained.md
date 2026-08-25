# ADR-0037: `CLAUDE.md` is tracked and maintained, because hiding a file does not stop it drifting

- **Status:** Accepted
- **Date:** 2026-08-25
- **Supersedes:** [ADR-0036](0036-claude-md-is-local-only.md)
- **Amends:** —

## Context

[ADR-0036](0036-claude-md-is-local-only.md), taken earlier the same day, untracked
`CLAUDE.md`. Its central argument was drift: the file summarises `docs/` and can contradict
it, and a public reader cannot tell which copy is stale.

Two things came to light within hours of that decision, and together they reverse it.

**Untracking removes nothing.** `CLAUDE.md` is in the repository's first commit and in the
head commit of all 26 merged pull requests. GitHub serves those at `refs/pull/N/head`
permanently and a force-push does not touch them — verified by fetching PR #4's head and
reading the file straight out of it, 11,222 bytes. Rewriting `main` would change 28 commit
SHAs and leave the file exactly as reachable. The only complete removal is deleting and
recreating the repository, which destroys the 26 pull requests and their review history —
the most legible record the project has of how it was actually built.

So the privacy question was never live. What remained was only whether the file is a good
document, and there the audit was damning in a way that points the other way.

**The file was materially false, in four claims, in its opening section**, and the drift was
worse than the review that prompted ADR-0036 had found:

| It claimed | Reality on 2026-08-25 |
|---|---|
| "Greenfield — no code exists yet" | 27 `src/main` Java files |
| "There is no POM and no source tree" | `pom.xml`, seven modules |
| "no remote configured" | remote since 2026-07-26, 26 merged PRs |
| "ADR-0002 … ADR-0014" | 37 ADRs |

And one stale line was not merely wrong but dangerous: the watcher guidance still said
*"resolve symlinks to their real path"*, an instruction
[ADR-0024](0024-watch-the-symlink-s-directory-not-its-real-path.md) had reversed after the
Task 0.8 spike showed it cannot see a Kubernetes ConfigMap swap the link target. A future
session following this file would have reintroduced a bug the project had already found and
fixed.

## Forces

- **Drift is an argument for tracking, not against it.** This is where ADR-0036 went wrong.
  A tracked file changes through pull requests and gets read; an untracked one drifts in
  private with no reviewer and no diff. `CLAUDE.md` reached four false statements *while
  nominally tracked* precisely because nobody treated it as a document with an audience.
  Untracking would have removed the one mechanism capable of catching that, and left the
  false version as the last public copy.
- **A stale guidance file is not inert.** It is read and acted on. The symlink instruction is
  the proof: the cost of staleness here is reintroduced bugs, which is strictly worse than
  the cost of a reader seeing an out-of-date summary.
- **Tracking it is the ecosystem convention**, and it is already this repository's own
  pattern: `.claude/settings.json` is tracked and `settings.local.json` is ignored, the
  shared/personal split that `CLAUDE.md` and its local variants follow. Untracking the shared
  file breaks a rule the repository otherwise keeps.
- **It is a strong artifact for a public repository.** The decision workflow, the
  one-branch-per-task rule and the "say no to these" scope boundaries are specified here. The
  outside review's most positive findings were about that discipline; this is where it is
  written down.
- **Against, and it survives as a standing obligation rather than a reason to hide:** two
  copies of anything can disagree. The answer is that the summaries here are explicitly
  pointers, the ADR wins where they differ, and a stale line is a defect to fix rather than
  background noise.
- **What the review actually asked for was a conscious choice**, not a deletion — it called
  publishing a `CLAUDE.md` "entirely normal and increasingly common". Removal went beyond the
  finding.

## Decision

**`CLAUDE.md` is tracked, public, and maintained in the same commit as the work it
describes.**

The file was brought current as part of this decision: the project state rewritten from
"greenfield" to v1-complete-and-public, the ADR and milestone ranges corrected, the symlink
guidance replaced with ADR-0024's actual conclusion, the capability model corrected to
ADR-0021's three values, and the working practices updated for a public repository.

**A stale instruction in this file is a defect.** It is not documentation drift to be
tolerated; a future session will follow it.

No history is rewritten. `CLAUDE.md` stays where it has always been, and the pull-request
record stays intact.

## Consequences

- The guidance file is subject to review like everything else, which is what makes drift
  catchable. It is now the *only* thing that makes drift catchable for this file.
- **Every change that invalidates a line here must fix that line in the same commit.** This
  is a real ongoing cost and it is the cost ADR-0036 was trying to avoid paying. It is
  cheaper than the alternative, which is guidance that quietly instructs the next session to
  undo finished work.
- The repository presents a coherent account including how it is worked on, which for a
  project whose distinguishing feature is its decision discipline is an asset rather than
  noise.
- **The dead link ADR-0036 created in ADR-0025 is alive again**, because its target is
  tracked once more. ADR-0036's body still says the link is dead; that body is frozen and
  stays as written. This ADR is where a reader learns it was reversed — which is the
  supersede mechanism working, not a defect in it.
- ADR-0036 was accepted and superseded within a few hours. That is recorded rather than
  tidied away: the reasoning trail is worth more than an appearance of having got it right
  first time, and the reason it was wrong — that hiding a file does not stop it drifting — is
  the part worth keeping.
