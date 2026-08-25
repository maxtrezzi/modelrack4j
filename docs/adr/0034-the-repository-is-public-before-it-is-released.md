# ADR-0034: The repository is public before it is released

- **Status:** Accepted
- **Date:** 2026-08-25
- **Supersedes:** —
- **Amends:** —

## Context

[D2](../tasks/open-decisions.md#d2--repository-visibility) had been open since the project
began: public from day one, or public at first release? It narrowed twice before it was
answered.

It narrowed the first time when the repository turned out to already exist on GitHub —
created 2026-07-26, private, with all work merged through pull requests. "Public from day
one" stopped being available; the live choice became *public now* or *public at first
release*.

It narrowed the second time when [Task 0.7](../tasks/phase-0-verification.md#task-07--name-and-coordinates)
established that Maven Central publishing does **not** require this repository to be public
— namespace verification uses a separate temporary repository
([ADR-0025](0025-fix-coordinates-under-io-github-maxtrezzi.md)). That removed the one thing
that might have forced the answer, leaving only two real arguments for opening it: JitPack
requires a public repository, and the ADRs are written to be read.

What changed by 2026-08-25 is that there is now something to read. v1 is complete — M0
through M5 — with 33 ADRs, a two-part manual, four runnable examples, and, as of
[P6](../tasks/post-v1.md#p6--the-integration-tests-against-live-apis), all four providers
verified against live APIs rather than against expectations.

The owner flipped the repository to public on 2026-08-25. This ADR records the decision and
its boundary, since D2's own closing instruction was to write one.

## Forces

**The ADRs only pay off when someone can read them.** A large part of what makes an
unofficial, independent library credible is a legible rationale record
([ADR-0002](0002-scope-to-langchain4j-llm-configuration.md),
[ADR-0011](0011-independent-name-and-deferred-wrapper.md)) — including the scope
boundaries it refuses to cross. Thirty-three ADRs behind a private wall are documentation
written for an audience of one.

**Distribution has no other intermediate route.** M6 (GPG signing, Central Portal
publishing) is deliberately unscheduled — it is triggered by the library proving itself in
the owner's first real project, not by a date. Until then JitPack is the only way anyone
else can depend on this, and JitPack needs a public repository.

**Against, and it is a real argument: the API has never met its consumer.** The plan's
governing rule is that when library and application disagree, *the application wins and the
library changes*. The first consumer application has not yet run on this library. Whatever
it demands, the library changes — and from now on that churn happens where it can be seen.

**Visibility is one-way.** Private can become public; public cannot become unseen. Clones,
forks, and caches outlive any later reversal.

Two alternatives were considered and lost:

- **Public at first release.** Rejected as a distinction that collapses on inspection: the
  trigger it would wait for is the one M6 already carries, so it adds no new gate while
  keeping the rationale record unreadable for a period governed by a *different* project's
  schedule.
- **Public and tagged `v0.1.0` today.** Rejected because it answers a question nobody asked.
  Making the source readable and committing to an API are separable acts, and only the
  first one was wanted.

## Decision

**The repository is public as of 2026-08-25, and it is not released.**

Those are two statements, and the second is as load-bearing as the first. The version stays
`0.1.0-SNAPSHOT`; there is no tag, no GitHub release, no artifact anywhere, and no
announcement. M6 keeps its own trigger, unchanged. A reader can read the source, read the
reasoning, and build it — nothing more is promised.

Three consequences of readability are now project rules rather than habits:

1. **`brainstorm/` is a confidentiality boundary, not a tidiness convention.** It was
   already git-ignored and never committed; the cost of a slip is now immediate and
   irreversible rather than embarrassing.
2. **Secret discipline is pre-push, not pre-release.** A credential committed from here on
   is public the moment it is pushed. The ignore rules from
   [P5](../tasks/post-v1.md#p5--repository-hygiene-ignore-rules-and-a-contributing-guide)
   are what stands between the working tree and that outcome. A full-history scan over all
   28 commits on 2026-08-25 found no key-shaped strings, none of the four live provider
   credentials, and no tracked `brainstorm/` content — that is the state being made public,
   and it is the last time such a scan can be a precondition rather than a post-mortem.
3. **History can no longer be quietly rewritten.** Rebasing away a bad commit stops being a
   local operation the moment someone else has fetched it.

## Consequences

**Gained.** JitPack becomes available if the library needs sharing before M6. The ADRs,
the manual, and the task records become the public rationale they were written to be. Bug
reports and questions can arrive from outside the project.

**Accepted.** API churn driven by the first consumer application is now visible churn, and
the CHANGELOG has to carry it honestly rather than silently. Anyone who finds the
repository before M6 finds something they cannot `mvn install` from Central — the README
must keep saying so plainly rather than reading like a shipped library.

**Foreclosed.** Reverting to private is available as a GitHub setting and useless as a
remedy. This decision is not revisited by flipping the switch back; it would be revisited
by a new ADR explaining what went wrong, with the old state still out there.

**Do not "simplify" the gap between public and released.** The absence of a `v0.1.0` tag is
the decision, not an oversight to be tidied up by a future contributor who notices the
repository looks unfinished. Tagging it turns a readable work-in-progress into a version
someone can reasonably expect to keep working, and that expectation is exactly what M6's
trigger exists to earn first.
