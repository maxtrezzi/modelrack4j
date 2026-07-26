# ADR-0015: Track work items in `docs/tasks/`, alongside the ADRs

- **Status:** Accepted
- **Date:** 2026-07-26
- **Supersedes:** —
- **Amends:** —

## Context

[ADR-0001](0001-record-decisions-as-adrs.md) established two homes for project knowledge:
private discussion logs, and tracked ADRs carrying rationale. The first commit exposed a
third category neither covers — **what to do next**.

The symptom was concrete. Tracked files cite work items by number — "Task 0.8 spike gates
M3", "Milestones run M0 → M5", "§9 lists open items" — while every one of those items lived
only in the untracked planning document. A reader with the repository and nothing else hit
identifiers that resolve to nothing.

## Forces

- Dangling references in a repository intended to be public are worse than absent ones. A
  citation implies something citable exists.
- Work items are not private in the way discussion is. "Pin the LangChain4j version" and
  "M3 delivers hot reload" reveal nothing the ADRs do not already state, and a public
  roadmap supports the independent-library positioning the project depends on.
- Keeping the backlog only in the planning document means every status update happens
  somewhere no contributor can see, and the repository silently disagrees with reality —
  the same failure mode [ADR-0014](0014-lifecycle-of-removed-names-and-superseded-bundles.md)
  refuses to accept for the registry.
- Against a tracked backlog: it duplicates the planning document, and duplication drifts.
- Renumbering was available and rejected. The existing identifiers are already cited from
  `CLAUDE.md` and from ADR-0013; a tidier scheme would have meant editing accepted ADRs to
  chase it.
- Issue trackers were also available. There is no remote yet, and repository visibility is
  itself an open decision — moving the backlog off-repo before that is settled would
  prejudge it.

## Decision

Work items live in `docs/tasks/`, tracked, mirroring the conventions of `docs/adr/`:
a folder README carrying the conventions and a status board, then one file per category —
Phase 0 verification tasks, milestones, and decisions awaiting the owner.

**Identifiers are preserved exactly** as the planning document uses them (`Task 0.4`,
`M3`), so existing citations resolve without edits, and they are never renumbered
afterwards.

The split of responsibility is explicit: **tasks say what to do and whether it is done;
ADRs say why the work is shaped that way.** A task that settles a design question closes by
writing an ADR and linking to it.

On duplication: `docs/tasks/` is authoritative for **status**; the planning document
remains the owner's working copy for original scoping. Where they disagree about what is
done, the tracked files win.

## Consequences

- The repository becomes self-contained — a contributor, or a future session with no access
  to the planning document, can see what is done, what is next, and what is blocked.
- Verification tasks now record their *findings*, not just completion. Several ADRs rest on
  assumptions Phase 0 is meant to confirm, and the task entries name which ADR each finding
  would amend — so a contradicted assumption has a defined route back into the record.
- Three tracked artifacts now describe the project instead of two, and they must be kept
  consistent. The mitigation is that each answers a different question, so a fact belongs to
  exactly one of them.
- Cost: status lives in git, so updating it means a commit. Accepted — that also makes
  progress auditable.
- Preserving the identifiers means inheriting their shape, including gaps and the slightly
  arbitrary `Task N.M` form. Judged cheaper than editing accepted ADRs to chase a tidier
  scheme.
