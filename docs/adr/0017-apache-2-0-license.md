# ADR-0017: License under Apache 2.0, with no NOTICE file

- **Status:** Accepted
- **Date:** 2026-07-27
- **Supersedes:** —
- **Amends:** —

## Context

The license was fixed in the plan's §2 decision table as Apache 2.0 and is named twice in
[the milestones](../tasks/milestones.md) — M0's header check and M5's POM metadata — but it
was never backfilled into an ADR alongside ADR-0002 … ADR-0014. Until now the choice had no
tracked rationale, only a tracked consequence.

Writing the `LICENSE` file is what forced the gap closed. Two constraints were already
fixed and are not reopened here: the project is an unofficial, independent library that
depends on LangChain4j ([ADR-0011](0011-independent-name-and-deferred-wrapper.md)), and its
first consumer is the owner's own closed application.

Relicensing is cheap now and expensive later — it needs the consent of every copyright
holder, and today there is exactly one. That makes this worth settling before the
repository takes outside contributions, and before it goes public
([D2](../tasks/open-decisions.md#d2--repository-visibility)).

## Forces

- **Patent grant.** Apache 2.0 §3 grants patent rights explicitly and terminates them on
  patent litigation. MIT says nothing about patents. For a library whose whole job is
  wiring up third-party LLM SDKs, an explicit grant is the substantive difference between
  the two, and the reason permissive-but-silent is not good enough.
- **Matching upstream.** LangChain4j is Apache-2.0 (verified against the upstream
  repository, 2026-07-27). A consumer taking both faces one set of terms rather than two.
- **Ecosystem expectation.** Apache 2.0 is the Java/Maven default, already on the
  pre-approved list at most corporate consumers, and carries the §5 contribution terms that
  matter once a public repository starts receiving pull requests.
- **Copyleft is disqualified, not merely rejected.** EPL or LGPL would encumber exactly the
  use this library exists for — embedding in a closed application, starting with the
  owner's own. The one stated purpose rules the family out.
- **Against a NOTICE file.** Apache 2.0 recommends but does not require one, and §4(d)
  obliges every downstream redistributor to propagate whatever it contains. This project
  bundles no third-party code, so a NOTICE would carry no attribution anyone needs while
  creating a permanent obligation for every consumer.

## Decision

License the project under the Apache License 2.0. Ship the canonical text verbatim as
`LICENSE` at the repository root, appendix included, with no substitutions into the
`Copyright [yyyy] [name of copyright owner]` placeholder — that placeholder is instruction
text for per-file headers, not part of the grant.

Do **not** add a `NOTICE` file. Add one only if third-party code is ever vendored in, and
only carrying that code's required attribution.

The concrete copyright owner string belongs in the per-file headers and the POM
`<licenses>`/`<developers>` metadata, which land with M0 and M5 respectively. It is not
needed for the grant to be effective and is deliberately not blocked on here.

## Consequences

- The repository is unambiguously licensed from before it has any code, so no commit is
  ever published under implicit all-rights-reserved.
- Consumers get an explicit patent grant, and one license to review rather than two.
- The cost accepted is Apache 2.0's ceremony: preserved notices, per-file headers, and a
  statement of changes. M0's license-header check exists to make that mechanical rather
  than remembered.
- **Do not add a NOTICE file for tidiness.** An empty or decorative one is not neutral — it
  binds every downstream redistributor to carry it. Its absence here is a decision, not an
  oversight.
- **Do not substitute a name into the LICENSE appendix.** Editing the canonical text makes
  automated license identification fail and gains nothing; the copyright holder is recorded
  in headers and POM metadata instead.
- Relicensing later requires the consent of all copyright holders. That is trivial today
  and stops being trivial the moment an outside contribution is merged.
