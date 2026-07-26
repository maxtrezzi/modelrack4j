# ADR-0001: Record decisions as ADRs; keep discussion logs out of the repo

- **Status:** Accepted
- **Date:** 2026-07-26
- **Supersedes:** —

## Context

The project's design rationale currently lives in a single planning document held outside
version control. That document proved its worth: its decision records exist specifically
so that constraints are not "simplified away" later by someone who cannot see what they
protect. But it is one growing file, it is local-only, and it cannot be referenced by a
commit, an issue, or a future contributor.

Meanwhile the planning material must stay unpublished, while the library itself is
intended to become public. Rationale therefore needs two homes, not one.

## Forces

- Decisions made in conversation evaporate. Six months on, the code shows *what* was built
  and nothing about which alternative was rejected or why — precisely the knowledge that
  stops a well-meaning refactor from undoing a deliberate constraint.
- Raw discussion is the wrong thing to publish: it contains dead ends, half-positions, and
  material the owner keeps private. Publishing it wholesale is not an option.
- Discarding the raw discussion is also wrong — the losing arguments are what make a
  decision auditable when it is later questioned.
- Appending everything to one planning file scales badly: no stable identifier per
  decision, no supersession trail, and the whole file is untracked, so nothing in it can
  ever be cited from the repository.
- A tracked-but-private folder is not a real option: anything committed is publishable.

## Decision

Two artifacts with different audiences and different lifetimes.

1. **`brainstorm/discussions/YYYY-MM-DD-topic.md`** — the working record of each
   substantive discussion: what was asked, what was weighed, what was rejected, what is
   still open. Local-only, never committed, kept verbatim enough to be useful later.
2. **`docs/adr/NNNN-title.md`** — the distilled decision, rewritten for publication in
   Forces → Decision → Consequences form. Tracked, numbered, immutable once accepted;
   changed only by a superseding ADR.

Every discussion produces (1). A discussion that settles something produces (2) as well.
Content moves from (1) to (2) by rewriting, never by copying.

## Consequences

- Decisions acquire stable identifiers, so commits, issues, and code comments can cite an
  ADR number instead of restating an argument.
- The public repository carries its own rationale, which is a large part of what makes an
  independent library credible to adopters.
- The private material stays private, and the boundary is mechanical: if it is in
  `brainstorm/`, it is not published.
- Cost: a discussion now has a deliverable beyond the code, and there is real judgement in
  deciding what clears the ADR bar. Recording too little is the failure mode to guard
  against; a short ADR beats none.
- The existing planning document's decision records are not retroactively converted by
  this ADR. They remain authoritative where they stand, and can be migrated later if the
  duplication starts to cost.

---

**Addendum, 2026-07-26** — the backfill anticipated in the last consequence was requested
and carried out the same day: the planning document's Appendix A became ADR-0002 through
ADR-0014. The body above is left as written, since the point of an accepted ADR is to show
what was known at the time. Where the planning document and a backfilled ADR now differ in
wording, the ADR is the citable record and the planning document remains the owner's
working copy.

The backfill also surfaced a case the Decision above does not cover: one record only
*partly* overrode an earlier one (ADR-0012 widened ADR-0008's swap scope without replacing
it). Supersession alone was too blunt, so the conventions gained an `Amends` header field
and a matching status, documented in the folder README. This extends the mechanism; it does
not change what was decided here.
