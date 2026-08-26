# ADR-0018: Manage LangChain4j versions by importing its BOM

- **Status:** Accepted — the BOM import set amended by [ADR-0022](0022-glm-via-the-community-module-and-its-bom.md)
- **Date:** 2026-07-28
- **Supersedes:** —
- **Amends:** —

## Context

[Task 0.1](../tasks/phase-0-verification.md#task-01--pin-the-langchain4j-version) set out to
pin the LangChain4j version in the root POM as a single `<langchain4j.version>` property.
Checking Maven Central showed that premise does not hold.

Upstream ships **two version lines from one release**. `langchain4j-bom:1.18.0` declares
`langchain4j.stable.version` = `1.18.0` for the stabilised modules, and
`langchain4j.beta.version` = `1.18.0-beta28` for integrations that have not stabilised.
Verified per artifact: `langchain4j-core`, `-open-ai`, `-anthropic` and `-google-ai-gemini`
resolve at `1.18.0`; `langchain4j-google-genai` has no `1.18.0` at all and is only
available as `1.18.0-beta28`.

So the version is not one string, and any mechanism assuming it is will either exclude the
beta-line modules or silently drift the two lines apart on the first bump. This project
plans on four provider modules ([ADR-0005](0005-provider-factory-spi-via-serviceloader.md)),
at least one of which — the Gemini choice still open in
[Task 0.4](../tasks/phase-0-verification.md#task-04--which-gemini-module) — may land on the
beta line.

## Forces

- **Hand-pinning each artifact** is explicit and greppable, but with two lines it means two
  properties maintained by hand across a multi-module build, and a bump that updates one and
  forgets the other produces a mismatched dependency set that still resolves. The failure is
  silent, which is the worst kind.
- **The BOM already encodes the mapping.** Upstream maintains which of its 115 artifacts sit
  on which line, and that mapping changes as modules stabilise. Restating it locally means
  re-deriving it on every upgrade, and being wrong between upgrades.
- **A module stabilising is invisible under hand-pinning.** When `-google-genai` eventually
  graduates to the stable line, a BOM import picks it up on the next bump; a hand-pinned
  `-beta28` keeps resolving an old beta indefinitely because nothing errors.
- **Against the BOM:** importing it accepts upstream's grouping wholesale, including for
  artifacts this project does not use, and makes the resolved version of any given artifact
  one indirection less obvious at the point of use.
- Dependency convergence is already enforced in M0, so a BOM that quietly changes a
  transitive version surfaces at build time rather than at runtime.

## Decision

Import `dev.langchain4j:langchain4j-bom` in the root POM's `<dependencyManagement>` with
scope `import`, type `pom`, and let it manage every LangChain4j artifact version.

Keep exactly one `<langchain4j.version>` property, whose only job is the BOM's own
coordinate. Bumping LangChain4j is then a one-line change that moves both version lines
together.

**Module POMs declare LangChain4j dependencies without a `<version>`.** A version tag on a
LangChain4j dependency anywhere outside the BOM import is a defect.

Pin `1.18.0` (published 2026-07-17, checked 2026-07-28).

Track the stable line. Depending on a beta-line artifact is allowed only where no stable
equivalent exists, and is a decision to be recorded — not something to arrive at by
accident because the BOM made it resolve.

## Consequences

- Upgrades are one line, and the stable and beta lines cannot drift apart, because the
  project no longer stores the mapping between them.
- Modules that stabilise upstream are picked up on the next bump instead of silently
  resolving a stale beta.
- **This does not widen what core depends on.** `dependencyManagement` fixes versions
  without adding dependencies, so `modelrack4j-core`'s restriction to `langchain4j-core` and
  `com.typesafe:config` ([ADR-0005](0005-provider-factory-spi-via-serviceloader.md)) is
  untouched. Importing the BOM is not depending on 115 artifacts.
- Choosing a beta-line module becomes a visible, arguable choice. This is live for
  [Task 0.4](../tasks/phase-0-verification.md#task-04--which-gemini-module): the newer
  `-google-genai` is still beta after 28 betas while `-google-ai-gemini` is stable, and that
  is a real input to which one this project builds on.
- The cost accepted is one indirection — no module POM states the LangChain4j version it
  gets, so answering "which version resolves here?" means `mvn dependency:tree` rather than
  reading the POM.
- **Do not "clarify" a module POM by adding an explicit `<version>` to a LangChain4j
  dependency.** It reads like documentation and behaves like a pin that outlives the next
  bump, reintroducing exactly the silent drift this decision removes.
