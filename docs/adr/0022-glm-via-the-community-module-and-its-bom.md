# ADR-0022: Take GLM from the community module, and import its BOM alongside the main one

- **Status:** Accepted
- **Date:** 2026-08-20
- **Supersedes:** —
- **Amends:** ADR-0018 — one BOM import becomes two; [ADR-0021](0021-token-estimation-is-universal-but-two-cost-classes.md) — the premise that every provider ships an estimator

## Context

[Task 0.3](../tasks/phase-0-verification.md#task-03--glm-module-status) asked whether a
maintained LangChain4j module for Zhipu GLM exists, on the working assumption — recorded in
the task from upstream issue langchain4j/langchain4j#3606 — that
`langchain4j-community-zhipu-ai` had been *removed* in community `1.3.0`. If that were true,
[D1](../tasks/open-decisions.md#d1--glm-route-if-no-maintained-module-exists) would go live
and GLM would have to be built on `langchain4j-open-ai` against Zhipu's OpenAI-compatible
endpoint.

**The removal never happened.** The project lead answered that exact issue on 2025-08-28:
*"They were not removed, they are released under `1.3.0-beta9` version."* The module is
current — `1.19.0-beta29`, published 2026-08-19, one day before this check — it tracks the
mainline release train version for version, and it lives at
`models/langchain4j-community-zhipu-ai/` in the `langchain4j-community` repository.

The "removal" was a version-numbering artefact. Community modules ship only beta-suffixed
versions, so anyone looking for a plain `1.3.0` gets a 404 and reasonably concludes the
module is gone. This project has already been bitten by the same shape of mistake:
`langchain4j-google-genai` has no plain `1.18.0` either
([ADR-0018](0018-manage-langchain4j-versions-via-bom.md)).

Two facts about the module then force a decision rather than a simple "use it":

1. It is **absent from `langchain4j-bom`** — zero matches across that BOM's 116 managed
   artifacts. Its version is managed by a *separate* `langchain4j-community-bom`, published
   at the same `1.19.0-beta29` coordinate.
2. It is **beta-line only**, with no stable equivalent, which ADR-0018 permits but requires
   to be recorded as a decision rather than arrived at by accident.

## Forces

- **The OpenAI-compatible fallback is still available**, and it stays on the stable line and
  adds no BOM. But it reaches GLM through an OpenAI-shaped API, so GLM-specific parameters
  are unreachable, and it rests on a compatibility guarantee Zhipu can withdraw without
  notice. The native module is a first-class client with `ZhipuAiChatRequestParameters`.
- **Against a second BOM import:** ADR-0018 sold a single coordinate as the thing that keeps
  version lines from drifting, and two coordinates is two things to bump. The saving grace is
  that they are bumped *together* and are trivially diffable — `1.19.0` and `1.19.0-beta29`
  visibly belong to one release train.
- **Hand-pinning the GLM artifact instead** would avoid the second import, but it is exactly
  the silent-drift failure ADR-0018 exists to prevent, and it would leave a `<version>` on a
  LangChain4j dependency — which that ADR calls a defect outright.
- **Beta is a real risk and not a formality.** A beta-suffixed artifact may break its API on
  any release. It is confined to one provider module, which is the mitigation: the blast
  radius of an upstream break is `modelrack4j-provider-glm`, never core.
- **Waiting for a stable GLM module** is not an option with a date on it. Community modules
  have been beta-only for their entire history — 31 releases from `1.0.0-alpha1` to
  `1.19.0-beta29` without a stable one.

## Decision

**Build `modelrack4j-provider-glm` on `dev.langchain4j:langchain4j-community-zhipu-ai`.**
D1's OpenAI-compatible fallback is not taken and D1 closes without a ruling.

**Import `langchain4j-community-bom` in the root POM's `<dependencyManagement>`**, alongside
`langchain4j-bom`, both with scope `import` and type `pom`. Two BOM coordinates, two
properties, bumped together. Every rule ADR-0018 laid down still holds: module POMs declare
LangChain4j dependencies without a `<version>`, and a version tag on one anywhere outside a
BOM import is a defect.

**This is the recorded beta-line decision ADR-0018 asked for.** GLM is depended on at
`1.19.0-beta29` because no stable equivalent exists, not because a BOM made it resolve.
The dependency is confined to the GLM provider module.

**The GLM module's capabilities are `ChatModel` + `StreamingChatModel` and nothing else.**
It ships `ZhipuAiChatModel` and `ZhipuAiStreamingChatModel`, and **no `ModerationModel` and
no `TokenCountEstimator`**. `GlmProviderFactory.validate()` must therefore reject both
`moderation` and `memory.type = token-window` for GLM configurations.

## Consequences

- GLM is a first-class provider with its native parameter type, not an OpenAI impersonation,
  and the project does not depend on an endpoint compatibility guarantee it does not control.
- **ADR-0018's single-import claim is amended, not broken.** The mechanism is unchanged and
  the reason for it is unchanged; the count went from one to two because upstream publishes
  two release trains. A third would be a smell worth stopping at.
- **Bump both coordinates together, always.** A bump that moves `langchain4j-bom` and leaves
  `langchain4j-community-bom` behind resolves cleanly and mixes release trains — the same
  silent failure ADR-0018 was written to prevent, reintroduced one level up. The two
  properties belong in adjacent lines in the root POM with a comment saying so.
- **GLM makes ADR-0021's `ABSENT` arm real.** That ADR made token estimation three-valued
  (`ABSENT`/`LOCAL`/`REMOTE`) when all four then-known providers shipped an estimator, so
  `ABSENT` was a hypothetical branch. GLM is a genuine `ABSENT`, which retires the temptation
  to collapse the enum to a boolean. ADR-0021 is strengthened by this, not amended.
- **GLM is the second provider with no moderation**, alongside Anthropic and both Gemini
  modules. Only the OpenAI family fills the bundle's `Optional<ModerationModel>`.
- The GLM module drags in `io.jsonwebtoken:jjwt` and `com.google.guava:guava` as compile
  dependencies. Confined to the provider module, but it makes GLM the heaviest of the four —
  worth a note in the README, and worth watching against M0's dependency convergence rule.
- Its own dependency on `langchain4j-core` is the **stable** `1.19.0`, so the beta suffix
  describes the wrapper, not the core it binds to.
- **Do not "modernise" the GLM module away to `langchain4j-open-ai` because the beta suffix
  looks unfinished.** That is D1's rejected fallback, and it silently loses every
  GLM-specific parameter.
