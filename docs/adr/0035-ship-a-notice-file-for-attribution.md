# ADR-0035: Ship a NOTICE file, because §4(d) is the only attribution clause that reaches a binary

- **Status:** Accepted
- **Date:** 2026-08-25
- **Supersedes:** —
- **Amends:** [ADR-0017](0017-apache-2-0-license.md) — the decision not to ship a `NOTICE` file

## Context

An outside review of the public repository on 2026-08-25 challenged
[ADR-0017](0017-apache-2-0-license.md)'s "no NOTICE file" clause against a goal the owner
holds: **that a fork should credit the original.**

ADR-0017 did not overlook the NOTICE question. It has a force arguing against one and a
consequence warning that its absence "is a decision, not an oversight". But re-read what
that force actually asks:

> This project bundles no third-party code, so a NOTICE would carry no attribution anyone
> needs while creating a permanent obligation for every consumer.

That is the question *"does a NOTICE carry attribution someone else is owed?"* — the
vendored-code case, where a NOTICE is a vehicle for third parties. It is answered correctly.
It is not the same question as *"does a NOTICE make a fork credit me?"*, which was never
put, and which has a different answer.

The repository going public ([ADR-0034](0034-the-repository-is-public-before-it-is-released.md))
is what makes the difference matter. A private repository cannot be forked by a stranger.

## Forces

- **Source headers do not survive compilation.** Apache 2.0 §4(c) obliges a redistributor to
  retain copyright and attribution notices *"in the Source form of the Work"*. Every file
  here already carries one (`build/license-header.txt`, enforced at `validate` since M0). But
  a fork that ships only a compiled jar redistributes no Source form, so §4(c) reaches
  nothing. **§4(d) is the only clause in the licence that follows the work into a binary**,
  and it is conditional: it applies *"If the Work includes a NOTICE text file"*. No NOTICE,
  no clause, no obligation.
- **The cost ADR-0017 named is real and is not disputed.** §4(d) binds every downstream
  redistributor to propagate the NOTICE forever, including consumers who have no interest in
  the attribution. That is the price of the lever, and it is why the file is four lines: the
  burden scales with what is in it, so nothing goes in it that does not have to.
- **Rejected — rely on source headers alone.** The status quo. It works for source
  redistribution and fails silently for the case most likely to arise, which is someone
  publishing a derivative artifact.
- **Rejected — protect the name instead.** Apache 2.0 §6 grants no trademark rights, so the
  name `modelrack4j` is already not licensed away. But that protects the *name* against
  misuse; it does not produce *credit*, and the two are not substitutes.
- **A NOTICE in the repository root alone would be theatre.** It would reach anyone who
  clones and nobody who consumes an artifact — precisely inverting the case §4(d) exists for.

## Decision

**Ship a `NOTICE` file, and ship it inside the artifacts.**

The file is deliberately minimal — the project name, the copyright line matching the one the
per-file headers already carry, and the attribution notice itself. Nothing else goes in it.

`LICENSE` and `NOTICE` are copied into `META-INF/` of every jar by a `<resources>` entry in
the parent POM, so a consumer who never sees the repository still receives both. Verified by
reading them back out of the built jars, not assumed.

Everything else in ADR-0017 stands unchanged: Apache 2.0, the canonical text shipped verbatim
as `LICENSE`, no substitution into the appendix placeholder, and the copyright owner string
living in per-file headers and POM metadata.

## Consequences

- A fork that distributes a derivative work — in source or binary form — must reproduce the
  attribution notice. That is the whole point, and it is now enforceable rather than hoped
  for.
- **Every consumer who redistributes carries the NOTICE forever.** Accepted knowingly. This
  is the obligation ADR-0017 declined to create, and creating it is the decision.
- **ADR-0017's warning inverts rather than disappears.** It said *"do not add a NOTICE file
  for tidiness"*. The live risk is now the opposite one: **do not grow this file.** Every
  line added is a line every downstream redistributor is bound to. Third-party attributions
  go in only if third-party code is ever actually vendored in, which remains true and remains
  the only reason to touch it.
- Declaring `<resources>` in the parent replaces the default `src/main/resources` entry
  rather than adding to it, so that entry is restated alongside. **Do not delete it as
  redundant** — every provider module's `META-INF/services/…ProviderFactory` file lives
  there, and losing it breaks `ServiceLoader` discovery with no compile error and no test
  failure that names the cause.
- The NOTICE is one more thing that must stay consistent with the per-file headers and the
  POM metadata. Three places now state the copyright owner.
