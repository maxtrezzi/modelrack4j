# ADR-0040: Protect `main` with required checks, not required review

- **Status:** Accepted
- **Date:** 2026-08-27
- **Supersedes:** —
- **Amends:** [ADR-0016](0016-one-feature-branch-per-task.md) — settles the merge strategy
  and pull-request question it deliberately left open

## Context

[ADR-0016](0016-one-feature-branch-per-task.md) settled that every task gets its own branch
and nothing is committed directly to `main`, but left one thing open: "merge strategy and
whether pull requests are used are deliberately left open — they depend on repository
visibility, which is still undecided (D2)." `CONTRIBUTING.md` already states the practice in
prose — "one branch per change, never committed straight to `main`" and "the build stays
green" — but until now nothing on GitHub enforced either sentence. Checking
`repos/.../branches/main/protection` returned `404 Branch not protected`.

D2 is now settled: the repository has been public since
[ADR-0034](0034-the-repository-is-public-before-it-is-released.md). That removes the reason
ADR-0016 gave for leaving this open, so it is decided now rather than staying open
indefinitely. This does not amend ADR-0016 — its decision (one branch per task, nothing
direct to `main`) is unchanged; this ADR is the decision it deferred.

The repository has exactly one collaborator, the owner, with the `admin` role — confirmed via
`gh api repos/.../collaborators` rather than assumed.

## Forces

**The CI workflow already defines what "green" means.** `.github/workflows/build.yml` runs
five checks on every push and pull request against `main`: `JDK 17`, `JDK 21`, `JDK 25`,
`docs consistency`, and `offline, no API keys` (job/check names read from the workflow file
and cross-checked against the actual check-runs on `main`'s current head, not assumed).
Requiring them turns "the build stays green" from a promise in `CONTRIBUTING.md` into
something GitHub itself refuses to merge around.

**A single maintainer makes "required review" a fiction.** With one collaborator, requiring
an approving review either blocks the owner outright or requires a second account solely to
rubber-stamp their own change — ceremony with no real second reviewer. Requiring a pull
request while setting the required approving-review count to zero keeps the visible-history
benefit (every change arrives as a reviewable unit, matching ADR-0016) without inventing a
reviewer that does not exist.

**`enforce_admins` is not selective.** GitHub's admin-bypass setting is all-or-nothing: turned
off, it exempts admins from every rule in the protection record alike — required checks,
required pull request, force-push protection, and deletion protection — not just the review
requirement (confirmed against GitHub's own documentation and changelog, not from
recollection). The two alternatives considered:

- **`enforce_admins: true`.** Rejected for now: it would route the owner's own solo work
  through the full CI-plus-PR path with no second collaborator to review anything, for no
  present benefit — there is nothing today that would be caught by the review that would not
  already be caught by the CI checks.
- **Requiring ≥1 approving review.** Rejected: with a single collaborator this is only
  satisfiable by the owner approving their own change or by bypassing as admin, neither of
  which is a real review.

## Decision

Branch protection is enabled on `main`:

- **Required status checks**, strict (the branch must be up to date before merging):
  `JDK 17`, `JDK 21`, `JDK 25`, `docs consistency`, `offline, no API keys`.
- **A pull request is required before merging**, with the required approving-review count
  set to `0` — the PR is mandatory, an approval is not.
- **Force pushes and branch deletion are disallowed** on `main`.
- **`enforce_admins` is `false`.** The owner, as the sole admin, is exempt from all of the
  above — this is a GitHub-wide property of the setting, not a choice to exempt only some
  rules.

## Consequences

**Gained.** The two sentences ADR-0016 and `CONTRIBUTING.md` already stated as practice —
one branch per task, and a green build before merging — are now enforced by GitHub for
anyone who is not an admin, rather than resting on the contributor having read the docs.

**Accepted.** Because the repository's only collaborator is an admin, none of this actually
constrains the owner's own workflow today — not the required checks, not the mandatory PR,
and not the force-push or deletion protection. The protection is inert for its author and
becomes load-bearing only once a collaborator without the admin role exists. This is a known
and accepted gap, not an oversight: it was the explicit trade-off behind choosing
`enforce_admins: false` over `true`.

**Do not "fix" the required-checks list by renaming it to fewer, broader entries without
checking `main`'s live check-runs first.** The five contexts above are the job/check *names*
`build.yml` currently produces, not a description of its structure — renaming a job in the
workflow silently breaks the merge gate (every PR becomes unmergeable, with no error pointing
at this ADR) rather than just changing what CI reports. A renamed job's check needs to be
added to this protection record in the same change that renames it.

**Foreclosed.** Nothing permanently — `enforce_admins` can be flipped to `true` later, e.g.
if a non-admin collaborator joins and the owner wants the same rules to bind their own
pushes too. That is a configuration change, not a reversal of this decision, and does not by
itself need a new ADR unless it changes which checks or review policy this ADR states.
