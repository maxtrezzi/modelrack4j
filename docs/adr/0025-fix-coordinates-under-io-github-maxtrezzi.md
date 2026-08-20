# ADR-0025: Fix the coordinates at `io.github.maxtrezzi:modelrack4j-*`

- **Status:** Accepted
- **Date:** 2026-08-20
- **Supersedes:** —
- **Amends:** —

## Context

[ADR-0011](0011-independent-name-and-deferred-wrapper.md) settled the *name* `modelrack4j`
and collision-checked it. It did not settle the **coordinates**, and
[Task 0.7](../tasks/phase-0-verification.md#task-07--name-and-coordinates) left the owner
segment of `io.github.<owner>` open pending confirmation that it was actually available.

M0 writes those coordinates into every module POM, and they are the one thing a consumer
copies into their own build. Changing them after publication means every consumer edits their
POM, so they are deliberately fixed before the skeleton exists rather than after.

Verified against Maven Central and Sonatype's own documentation on 2026-08-20:

- **`io.github.maxtrezzi` is unused.** Two independent Central queries return zero results,
  and no artifact named `modelrack*` exists anywhere on Central.
- **The namespace is not merely free — it is *this account's*.** Sonatype Central grants
  `io.github.<github username>` automatically on GitHub signup, with no verification steps.
  It is tied to the GitHub identity, so no one else can take it.
- The GitHub repository is `maxtrezzi/modelrack4j`, owned by the **user** account `maxtrezzi`
  (not an organisation), Apache-2.0, default branch `main`.

One constraint fell out of the same documentation and is the reason this is an ADR rather
than a line in a task: *"we only support the GitHub username that you used to sign up, so
`io.github.<github organization name>` is not available as an automatically registered
namespace."*

## Forces

- **A personal-account groupId reads as less "official"** than an organisation one, and the
  obvious future move — create a `modelrack4j` GitHub organisation and transfer the repo —
  is exactly what the quoted rule penalises. An org groupId needs manual verification, and if
  the coordinates were to follow the repo to the org, every consumer POM changes.
- **Against waiting for an organisation:** the library is unofficial and independent by
  design ([ADR-0011](0011-independent-name-and-deferred-wrapper.md)), its first consumer is
  the owner's own application, and Central publishing is deferred to a later milestone
  anyway. Blocking M0 on an organisation that may never be wanted trades a certain cost for a
  hypothetical benefit.
- **`io.github.*` is unglamorous but honest**, and it is what an independent single-maintainer
  library legitimately is. Consumers read the groupId as provenance, and this provenance is
  accurate.
- **Repository visibility is a separate question.** Namespace verification needs a *temporary*
  public repository holding a verification key, not a public project repository, so
  [D2](../tasks/open-decisions.md#d2--repository-visibility) does not gate the groupId either
  way.

## Decision

**Fix the coordinates at `io.github.maxtrezzi`, with artifacts `modelrack4j-*`:**

| Module | Coordinate |
|---|---|
| aggregate / parent | `io.github.maxtrezzi:modelrack4j-parent` |
| core | `io.github.maxtrezzi:modelrack4j-core` |
| providers | `io.github.maxtrezzi:modelrack4j-provider-{openai,anthropic,gemini,glm}` |

M0 writes these into the skeleton. The `langchain4j-` prefix is never used, per ADR-0011.

**Decoupling the groupId from the repository's location is deliberate.** If the repository
later moves to a GitHub organisation, **the groupId stays `io.github.maxtrezzi`.** The
groupId records who publishes the artifacts, not where the source is hosted, and those are
allowed to differ. Moving the groupId to follow a repository transfer would break every
consumer POM to buy nothing a consumer can observe.

## Consequences

- M0 is unblocked on coordinates; nothing about the skeleton waits on a naming question.
- **A future GitHub organisation is still available**, and costs nothing, because the groupId
  does not follow it. Whoever proposes that move should read this section first: the
  temptation will be to "tidy up" the groupId to match the new org, which is a breaking
  change for every consumer and requires manual namespace verification that the current
  coordinate does not.
- The namespace cannot be lost to a squatter, being bound to the GitHub account rather than
  claimed first-come.
- **Publishing is still deferred** ([CLAUDE.md](../../CLAUDE.md), local `-SNAPSHOT` installs
  only). This ADR fixes the coordinates; it does not schedule a release, and no Sonatype
  account has been created yet — the namespace is confirmed *available on the documented
  terms*, not yet claimed.
- D2 remains open and is genuinely independent: the project repository's visibility does not
  affect the groupId, because verification uses a separate temporary repository.
