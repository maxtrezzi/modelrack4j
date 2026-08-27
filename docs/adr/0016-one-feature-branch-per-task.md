# ADR-0016: One feature branch per task

- **Status:** Accepted
- **Date:** 2026-07-26
- **Supersedes:** —
- **Amends:** —

## Context

The repository now has a task list with stable identifiers
([ADR-0015](0015-track-work-items-in-docs-tasks.md)) and a rule that decisions become ADRs
([ADR-0001](0001-record-decisions-as-adrs.md)). What was unstated is how work reaches
`main` — and the first two commits went straight onto it, because nothing said otherwise.

## Forces

- A task rarely produces only code. It produces code, a status update in `docs/tasks/`, and
  sometimes an ADR. Those belong together: reviewing the change without the reasoning, or
  the status without the work, loses the connection that makes either legible.
- Committing directly to `main` means every intermediate state is the published state.
  Once the library has consumers, `main` needs to stay releasable.
- Task identifiers already exist and are stable by policy. Branch names can reuse them for
  free, giving a mechanical link from branch to work item without a second naming scheme to
  invent or maintain.
- Against: for a single-maintainer project, branching is ceremony, and a trivial
  documentation fix does not need review.
- But the ceremony is the point at the boundary that matters — Phase 0 tasks produce
  *findings* that amend ADRs, and those changes deserve to be seen as a unit rather than
  landing as an untraceable edit to an accepted record.

## Decision

Every task gets its own branch. Nothing is committed directly to `main`.

Branch names derive from the task identifier, so the branch says which work item it serves:

| Work | Branch |
|---|---|
| A Phase 0 verification task | `task/0.1-pin-langchain4j-version` |
| A milestone | `milestone/m0-skeleton` |
| An open decision | `decision/d2-repository-visibility` |
| Work with no task ID | `docs/<slug>` or `chore/<slug>` |

A branch carries the whole item: the work, its status update in `docs/tasks/`, and any ADR
it produces. It merges into `main` when the item is done, and is deleted after merging.

## Consequences

- `main` stays releasable, and its history reads as a sequence of completed work items
  rather than of intermediate saves.
- A finding that amends an accepted ADR arrives as a reviewable change alongside the
  evidence that produced it — the mechanism ADR-0015 relies on but could not enforce.
- Branch names are greppable against the task list, so an abandoned branch is visibly
  attached to an unfinished item.
- Cost: overhead on small changes, in a project with one maintainer. Accepted as the price
  of a uniform rule; a rule with a "unless it's small" exemption is one that erodes.
- Merge strategy and whether pull requests are used are deliberately left open — they
  depend on repository visibility, which is still undecided
  ([D2](../tasks/open-decisions.md#d2--repository-visibility)).
